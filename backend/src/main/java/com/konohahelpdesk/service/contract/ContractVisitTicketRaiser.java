package com.konohahelpdesk.service.contract;

import com.konohahelpdesk.model.company.Company;
import com.konohahelpdesk.model.contract.ContractVisit;
import com.konohahelpdesk.model.contract.ServiceContract;
import com.konohahelpdesk.model.contract.VisitStatus;
import com.konohahelpdesk.model.ticket.Ticket;
import com.konohahelpdesk.model.user.Roles;
import com.konohahelpdesk.model.user.User;
import com.konohahelpdesk.model.user.UserStatus;
import com.konohahelpdesk.repository.contract.ContractVisitRepository;
import com.konohahelpdesk.repository.user.UserRepository;
import com.konohahelpdesk.service.ticket.TicketService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;

/**
 * Raises the maintenance ticket for ONE scheduled contract visit, in a transaction of its own.
 *
 * It is a separate bean (not a method on ContractSweeper) because REQUIRES_NEW only takes effect
 * through the Spring proxy. The isolation is the point: when one visit cannot get its ticket —
 * the company's subscription is inactive, say — only that visit rolls back. The ticket and the
 * visit's link to it commit together, so a visit never ends up with a ticket it doesn't point at
 * (which the next sweep would raise a second time).
 */
@Service
public class ContractVisitTicketRaiser {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy");

    private final ContractVisitRepository visits;
    private final UserRepository users;
    private final TicketService tickets;

    public ContractVisitTicketRaiser(ContractVisitRepository visits, UserRepository users, TicketService tickets) {
        this.visits = visits;
        this.users = users;
        this.tickets = tickets;
    }

    /** @return false when the visit no longer needs a ticket (already raised, completed or gone). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean raise(Long visitId) {
        ContractVisit v = visits.findById(visitId).orElse(null);
        if (v == null || v.getTicket() != null || v.getStatus() != VisitStatus.SCHEDULED) return false;
        ServiceContract c = v.getContract();
        Ticket t = tickets.createForContract(
            c.getCompany(),
            raiser(c),
            c.getName() + " — scheduled visit " + v.getSequence() + " (" + v.getScheduledDate().format(DATE) + ")",
            describe(c, v),
            c.getTicketCategory(),
            c.getTicketPriority());
        v.setTicket(t);
        visits.save(v);
        return true;
    }

    private String describe(ServiceContract c, ContractVisit v) {
        StringBuilder b = new StringBuilder();
        b.append("Scheduled work under service contract \"").append(c.getName()).append("\".\n\n");
        if (c.getContractNumber() != null) b.append("Contract ref: ").append(c.getContractNumber()).append("\n");
        b.append("Company: ").append(c.getCompany().getName()).append("\n");
        b.append("Visit: ").append(v.getSequence()).append(" of ").append(c.getOccurrences())
            .append(" (").append(c.getFrequency().getLabel().toLowerCase(java.util.Locale.ROOT)).append(")\n");
        b.append("Scheduled for: ").append(v.getScheduledDate().format(DATE)).append("\n");
        b.append("Contract term: ").append(c.getStartDate().format(DATE)).append(" – ").append(c.getEndDate().format(DATE)).append("\n");
        if (c.getCoveredAssets() != null) b.append("\nCovered equipment:\n").append(c.getCoveredAssets()).append("\n");
        if (c.getDescription() != null) b.append("\n").append(c.getDescription()).append("\n");
        return b.toString();
    }

    /**
     * Whose name the auto-raised ticket goes out under. The contract owner if one is named,
     * otherwise the company's main engineer, otherwise any active admin — {@code Ticket.createdBy}
     * is non-null, so there has to be someone, and picking an admin is honest about the fact that
     * the provider raised it rather than the customer.
     */
    private User raiser(ServiceContract c) {
        if (c.getOwner() != null && c.getOwner().getStatus() == UserStatus.ACTIVE) return c.getOwner();
        Company company = c.getCompany();
        if (company.getMainEngineer() != null && company.getMainEngineer().getStatus() == UserStatus.ACTIVE) {
            return company.getMainEngineer();
        }
        return users.findByRoleAndStatus(Roles.ADMIN, UserStatus.ACTIVE).stream().findFirst()
            .orElseThrow(() -> new IllegalStateException("No active admin to raise the contract ticket as"));
    }
}
