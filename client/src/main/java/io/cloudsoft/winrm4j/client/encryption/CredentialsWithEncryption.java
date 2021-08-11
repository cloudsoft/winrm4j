package io.cloudsoft.winrm4j.client.encryption;

import org.apache.http.auth.NTCredentials;

public interface CredentialsWithEncryption {

    boolean isAuthenticated();
    void setIsAuthenticated(boolean isAuthenticated);

    void setClientKey(byte[] key);
    void setServerKey(byte[] key);

    byte[] getClientKey();
    byte[] getServerKey();

    public static class NTCredentialsWithEncryption extends NTCredentials implements CredentialsWithEncryption {

        boolean isAuthenticated = false;
        byte[] clientKey, serverKey;

        public NTCredentialsWithEncryption(String userName, String password, String workstation, String domain) {
            super(userName, password, workstation, domain);
        }

        @Override
        public boolean isAuthenticated() {
            return isAuthenticated;
        }

        @Override
        public void setIsAuthenticated(boolean isAuthenticated) {
            this.isAuthenticated = isAuthenticated;
        }

        @Override
        public void setClientKey(byte[] clientKey) {
            this.clientKey = clientKey;
        }

        @Override
        public void setServerKey(byte[] serverKey) {
            this.serverKey = serverKey;
        }

        @Override
        public byte[] getClientKey() {
            return clientKey;
        }

        @Override
        public byte[] getServerKey() {
            return serverKey;
        }
    }

}
