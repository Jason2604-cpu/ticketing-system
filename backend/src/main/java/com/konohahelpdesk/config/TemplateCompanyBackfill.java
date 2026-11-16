package com.konohahelpdesk.config;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves templates that were pinned to a single company onto the many-to-many join table.
 *
 * A ticket template used to carry one {@code company_id} column; it now has a
 * {@code ticket_template_companies} join table so one template can serve several customers.
 * ddl-auto=update creates the new table but never migrates data, and it never drops the old
 * column — which is what makes this recoverable: the original value is still sitting there.
 *
 * Raw SQL on purpose. The mapping no longer has a {@code company} field, so there is no JPQL that
 * can read the legacy column; and a migration should read what is actually in the database rather
 * than what the current entity says should be.
 *
 * Idempotent three times over: it does nothing if the legacy column is gone, copies only rows that
 * have a value, and skips any template that already has a row in the join table. Safe on every
 * later startup, and safe if someone has already re-scoped a template by hand.
 *
 * @Order(3) — after the seeders that create companies, so a copied company_id always resolves.
 */
@Component
@Order(3)
public class TemplateCompanyBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TemplateCompanyBackfill.class);

    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!legacyColumnExists()) return;

        int copied = em.createNativeQuery("""
            insert into ticket_template_companies (template_id, company_id)
            select t.id, t.company_id
            from ticket_templates t
            where t.company_id is not null
              and not exists (select 1 from ticket_template_companies x where x.template_id = t.id)
            """).executeUpdate();

        if (copied > 0) {
            log.info("Backfilled {} ticket template(s) onto ticket_template_companies. The legacy "
                + "ticket_templates.company_id column is now unused and can be dropped by hand.", copied);
        }
    }

    /**
     * A fresh database never had the column, so this is not an error condition — it is the normal
     * state for every installation created after the change.
     */
    private boolean legacyColumnExists() {
        try {
            Number n = (Number) em.createNativeQuery("""
                select count(*) from information_schema.columns
                where table_name = 'ticket_templates' and column_name = 'company_id'
                """).getSingleResult();
            return n != null && n.intValue() > 0;
        } catch (RuntimeException e) {
            log.warn("Could not check for the legacy template company column: {}", e.getMessage());
            return false;
        }
    }
}
