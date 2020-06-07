package com.checkmarx.jenkins.test;

import com.checkmarx.jenkins.Aes;
import com.checkmarx.jenkins.CxCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class PasswordEncryptTest {

    private final String EXPECTED_PASSWORD = "Cx123456!";
    private final String MESSAGE = "CxSAST Password";

    @Mock
    CxCredentials credentials;

    @BeforeEach
    void init() {
        final String USERNAME = "admin@cx";
        Mockito.when(credentials.getUsername()).thenReturn(USERNAME);
        String encryptedPassword = Aes.encrypt(EXPECTED_PASSWORD, credentials.getUsername());
        Mockito.when(credentials.getPassword()).thenReturn(encryptedPassword);
    }

    @Test
    void encryptDecryptPassword_ValidProcess() {
        String actualResult = Aes.decrypt(credentials.getPassword(), credentials.getUsername());
        assertEquals(EXPECTED_PASSWORD, actualResult, MESSAGE);
    }

    @Test
    void encryptDecryptPassword_InvalidProcess() {
        String actualResult = Aes.decrypt(credentials.getPassword(), credentials.getUsername());
        assertNotEquals("Cx123456", actualResult, MESSAGE);
    }
}
