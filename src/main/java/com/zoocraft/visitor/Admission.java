package com.zoocraft.visitor;

import com.zoocraft.core.ledger.LedgerResult;
import com.zoocraft.feedback.CommentType;
import com.zoocraft.zoo.Zoo;
import com.zoocraft.zoo.ZooManager;

import java.util.List;

/** Group admission (spec section 7.4): everyone pays, or nobody does. */
public final class Admission {
    private Admission() {
    }

    /**
     * Decides and charges admission for a group waiting at a gate.
     *
     * @return true when the whole group is now admitted
     */
    public static boolean tryAdmitGroup(ZooManager zoos, Zoo zoo, List<Visitor> group) {
        if (!zoo.open() || group.isEmpty()) {
            return false;
        }
        int price = zoo.admissionPrice();
        for (Visitor member : group) {
            if (!member.admitted() && !member.profile().accepts(price, member.budget(), zoo.reputation(), member.personalFactor())) {
                member.say(CommentType.TOO_EXPENSIVE);
                zoos.recordRefusal(zoo, group.size());
                return false;
            }
        }
        for (Visitor member : group) {
            if (member.admitted()) {
                continue;
            }
            LedgerResult result = zoos.chargeAdmission(zoo, member.visitId(), price);
            if (result == LedgerResult.APPLIED) {
                member.markAdmitted(price);
            } else if (result == LedgerResult.DUPLICATE) {
                // Already paid for this visit; never charge or deduct twice.
                member.markAdmitted(0);
            }
        }
        return true;
    }
}
