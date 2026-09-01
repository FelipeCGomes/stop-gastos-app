package com.example.stop_fgastos.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FamilyState {
    private final String familyId;
    private final String familyName;
    private final String ownerUid;
    private final String role;
    private final List<FinanceRecord> members;
    private final List<FinanceRecord> invitations;
    private final List<FinanceRecord> sharedLists;

    public FamilyState() {
        this("", "", "", "", Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
    }

    public FamilyState(
            String familyId,
            String familyName,
            String ownerUid,
            String role,
            List<FinanceRecord> members,
            List<FinanceRecord> invitations,
            List<FinanceRecord> sharedLists
    ) {
        this.familyId = familyId == null ? "" : familyId;
        this.familyName = familyName == null ? "" : familyName;
        this.ownerUid = ownerUid == null ? "" : ownerUid;
        this.role = role == null ? "" : role;
        this.members = Collections.unmodifiableList(new ArrayList<>(members));
        this.invitations = Collections.unmodifiableList(new ArrayList<>(invitations));
        this.sharedLists = Collections.unmodifiableList(new ArrayList<>(sharedLists));
    }

    public String familyId() { return familyId; }
    public String familyName() { return familyName; }
    public String ownerUid() { return ownerUid; }
    public String role() { return role; }
    public List<FinanceRecord> members() { return members; }
    public List<FinanceRecord> invitations() { return invitations; }
    public List<FinanceRecord> sharedLists() { return sharedLists; }
    public boolean hasFamily() { return !familyId.isBlank(); }
    public boolean isAdmin() { return "admin".equals(role); }
}
