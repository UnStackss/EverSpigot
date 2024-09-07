package net.minecraft.util;

import com.mojang.logging.LogUtils;
import java.security.PrivateKey;
import java.security.Signature;
import org.slf4j.Logger;

public interface Signer {
    Logger LOGGER = LogUtils.getLogger();

    byte[] sign(SignatureUpdater updatable);

    default byte[] sign(byte[] data) {
        return this.sign(updater -> updater.update(data));
    }

    static Signer from(PrivateKey privateKey, String algorithm) {
        return updatable -> {
            try {
                Signature signature = Signature.getInstance(algorithm);
                signature.initSign(privateKey);
                updatable.update(signature::update);
                return signature.sign();
            } catch (Exception var4) {
                throw new IllegalStateException("Failed to sign message", var4);
            }
        };
    }
}
