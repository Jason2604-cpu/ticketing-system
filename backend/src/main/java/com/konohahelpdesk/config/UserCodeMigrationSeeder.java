package com.konohahelpdesk.config;

import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.security.AuditLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One-time renumbering of every existing user's code onto the merged EMP/CUS scheme:
 * ADMIN + ENGINEER -> EMPxxxxx, CUSTOMER + CUSTOMER_ADMIN -> CUSxxxxxx (replacing the old
 * ADM-/ENG-/CAD-/CUS- per-role prefixes). Runs on every startup but is idempotent — a user whose
 * code already matches the new pattern for their side is left untouched, so once every user has
 * been renumbered this becomes a no-op.
 */
@Component
public class UserCodeMigrationSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(UserCodeMigrationSeeder.class);
    private static final Pattern EMP_PATTERN = Pattern.compile("^EMP(\\d{5})$");
    private static final Pattern CUS_PATTERN = Pattern.compile("^CUS(\\d{6})$");

    private final UserRepository userRepository;
    private final AuditLogger audit;

    public UserCodeMigrationSeeder(UserRepository userRepository, AuditLogger audit) {
        this.userRepository = userRepository;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        List<User> all = userRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));

        int empSeq = 0;
        int cusSeq = 0;
        for (User u : all) {
            Matcher emp = EMP_PATTERN.matcher(u.getUserCode());
            if (emp.matches()) empSeq = Math.max(empSeq, Integer.parseInt(emp.group(1)));
            Matcher cus = CUS_PATTERN.matcher(u.getUserCode());
            if (cus.matches()) cusSeq = Math.max(cusSeq, Integer.parseInt(cus.group(1)));
        }

        int migrated = 0;
        for (User u : all) {
            boolean staff = u.getRole().isStaffSide();
            if (staff && !EMP_PATTERN.matcher(u.getUserCode()).matches()) {
                String code;
                do { code = String.format("EMP%05d", ++empSeq); } while (userRepository.existsByUserCode(code));
                u.setUserCode(code);
                migrated++;
            } else if (!staff && !CUS_PATTERN.matcher(u.getUserCode()).matches()) {
                String code;
                do { code = String.format("CUS%06d", ++cusSeq); } while (userRepository.existsByUserCode(code));
                u.setUserCode(code);
                migrated++;
            }
        }

        if (migrated > 0) {
            userRepository.saveAll(all);
            audit.event("USER_CODES_MIGRATED", "count=" + migrated + " scheme=EMP/CUS");
            log.warn("Renumbered {} user code(s) onto the merged EMP/CUS scheme.", migrated);
        }
    }
}
