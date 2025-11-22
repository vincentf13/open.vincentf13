package open.vincentf13.exchange.account.ledger.sdk.rest.api.enums;

public enum EntryType {
    DEPOSIT(ReferenceType.DEPOSIT),
    WITHDRAWAL(ReferenceType.WITHDRAWAL);

    private final ReferenceType referenceType;

    EntryType(ReferenceType referenceType) {
        this.referenceType = referenceType;
    }

    public String code() {
        return name();
    }

    public ReferenceType referenceType() {
        return referenceType;
    }
}
