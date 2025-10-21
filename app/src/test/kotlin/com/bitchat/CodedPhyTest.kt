package com.bitchat

import android.content.Context
import android.os.Build
import com.bitchat.android.mesh.CodedPhyManager
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*

/**
 * Unit tests for LE Coded PHY functionality
 */
class CodedPhyTest {

    @Test
    fun testCodedPhyManagerInitialization() {
        val mockContext = mock(Context::class.java)
        val codedPhyManager = CodedPhyManager(mockContext)
        
        // Should initialize without throwing
        assertNotNull(codedPhyManager)
        
        // Status info should be available
        val statusInfo = codedPhyManager.getStatusInfo()
        assertTrue(statusInfo.contains("LE Coded PHY Status"))
        assertTrue(statusInfo.contains("Android Version"))
    }
    
    @Test
    fun testCodedPhyAvailabilityLogic() {
        val mockContext = mock(Context::class.java)
        val codedPhyManager = CodedPhyManager(mockContext)
        
        // On older Android versions, should not be available
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            assertFalse("LE Coded PHY should not be available on Android < 8.0", 
                       codedPhyManager.isCodedPhyAvailable())
        }
        
        // Test enable/disable functionality
        val initialState = codedPhyManager.isCodedPhyAvailable()
        codedPhyManager.setCodedPhyEnabled(false)
        // After disabling, should not be available regardless of hardware support
        assertFalse("LE Coded PHY should not be available when disabled", 
                   codedPhyManager.isCodedPhyAvailable())
    }
    
    @Test
    fun testStatusInfoContent() {
        val mockContext = mock(Context::class.java)
        val codedPhyManager = CodedPhyManager(mockContext)
        
        val statusInfo = codedPhyManager.getStatusInfo()
        
        // Should contain key information
        assertTrue("Status should mention Android version requirement", 
                  statusInfo.contains("required: 26+"))
        assertTrue("Status should mention hardware support", 
                  statusInfo.contains("Hardware Support"))
        assertTrue("Status should mention feature enabled state", 
                  statusInfo.contains("Feature Enabled"))
        assertTrue("Status should mention availability", 
                  statusInfo.contains("Available"))
        
        // If available, should mention benefits
        if (codedPhyManager.isCodedPhyAvailable()) {
            assertTrue("Status should mention range extension", 
                      statusInfo.contains("Range Extension"))
            assertTrue("Status should mention coding schemes", 
                      statusInfo.contains("Coding Schemes"))
        }
    }
}