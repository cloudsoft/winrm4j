package io.cloudsoft.winrm4j.client;

public enum PayloadEncryptionMode {
    OFF(false,false), OPTIONAL(true,false), REQUIRED(true,true);

    final boolean permitted, required;
    PayloadEncryptionMode(boolean permitted, boolean required) {
        this.permitted = permitted;
        this.required = required;
    }

    public boolean isPermitted() { return permitted; }
    public boolean isRequired() { return required; }
}
