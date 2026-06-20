package com.aibos.identity.enums;

public enum Tier {
    FREE , PRO , ENTERPRISE;

    public boolean isPaid(){
        return this != FREE;
    }
}
