package com.checkmarx.jenkins.test;

import com.checkmarx.jenkins.Aes;
import com.checkmarx.jenkins.CxConnectionDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class PasswordEncryptTest {

    private final String EXPECTED_PASSWORD = "Pass246!";
    private final String MESSAGE = "CxSAST Password";

    @Mock
    CxConnectionDetails connDetails;

    @BeforeEach
    void init() {
        final String USERNAME = "my@user";
        Mockito.when(connDetails.getUsername()).thenReturn(USERNAME);
        String encryptedPassword = Aes.encrypt(EXPECTED_PASSWORD, connDetails.getUsername());
        Mockito.when(connDetails.getPassword()).thenReturn(encryptedPassword);
    }

    @Test
    void encryptDecryptPassword_ValidProcess() {
        String actualResult = Aes.decrypt(connDetails.getPassword(), connDetails.getUsername());
        assertEquals(EXPECTED_PASSWORD, actualResult, MESSAGE);
    }

    @Test
    void encryptDecryptPassword_InvalidProcess() {
        String actualResult = Aes.decrypt(connDetails.getPassword(), connDetails.getUsername());
        assertNotEquals("Pass245!", actualResult, MESSAGE);
    }
}
