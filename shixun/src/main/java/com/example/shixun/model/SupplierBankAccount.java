package com.example.shixun.model;

public class SupplierBankAccount {
    private Long id;
    private String receiverNo;
    private String supplier;
    private String accountType;
    private String accountName;
    private String bankAccount;
    private String bank;
    private String branch;
    private String location;
    private String note;

    public SupplierBankAccount() {}

    public SupplierBankAccount(Long id, String receiverNo, String supplier, String accountType, String accountName,
                               String bankAccount, String bank, String branch, String location, String note) {
        this.id = id;
        this.receiverNo = receiverNo;
        this.supplier = supplier;
        this.accountType = accountType;
        this.accountName = accountName;
        this.bankAccount = bankAccount;
        this.bank = bank;
        this.branch = branch;
        this.location = location;
        this.note = note;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getReceiverNo() { return receiverNo; }
    public void setReceiverNo(String receiverNo) { this.receiverNo = receiverNo; }
    public String getSupplier() { return supplier; }
    public void setSupplier(String supplier) { this.supplier = supplier; }
    public String getAccountType() { return accountType; }
    public void setAccountType(String accountType) { this.accountType = accountType; }
    public String getAccountName() { return accountName; }
    public void setAccountName(String accountName) { this.accountName = accountName; }
    public String getBankAccount() { return bankAccount; }
    public void setBankAccount(String bankAccount) { this.bankAccount = bankAccount; }
    public String getBank() { return bank; }
    public void setBank(String bank) { this.bank = bank; }
    public String getBranch() { return branch; }
    public void setBranch(String branch) { this.branch = branch; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
