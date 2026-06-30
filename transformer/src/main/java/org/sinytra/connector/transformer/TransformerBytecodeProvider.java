package org.sinytra.connector.transformer;

public interface TransformerBytecodeProvider {
    byte[] getByteCode(String className) throws ClassNotFoundException;
}
