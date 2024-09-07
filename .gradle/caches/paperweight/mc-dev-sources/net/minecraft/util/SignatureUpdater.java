package net.minecraft.util;

import java.security.SignatureException;

@FunctionalInterface
public interface SignatureUpdater {
    void update(SignatureUpdater.Output updater) throws SignatureException;

    @FunctionalInterface
    public interface Output {
        void update(byte[] data) throws SignatureException;
    }
}
