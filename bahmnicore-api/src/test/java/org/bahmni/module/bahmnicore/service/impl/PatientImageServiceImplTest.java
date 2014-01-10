package org.bahmni.module.bahmnicore.service.impl;

import org.bahmni.module.bahmnicore.BahmniCoreApiProperties;
import org.junit.Test;
import org.mockito.Mock;

import java.io.File;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class PatientImageServiceImplTest {

    private PatientImageServiceImpSubClass patientImageServiceImpSubClass;

    @Mock
    private BahmniCoreApiProperties properties;

    @Test
    public void shouldCreateRightDirectoryAccordingToPatientId() {
        initMocks(this);
        when(properties.getDocumentBaseDirectory()).thenReturn("");
        patientImageServiceImpSubClass = new PatientImageServiceImpSubClass(properties);

        String url = patientImageServiceImpSubClass.createFilePath(".", 280, "Radiology");

        assertFalse(url.isEmpty());
        assertTrue(url.startsWith("/300/280-Radiology-"));
        assertTrue(url.endsWith(".jpeg"));

        File absoluteFileDirectory = new File("./300");
        absoluteFileDirectory.delete();
    }

    private class PatientImageServiceImpSubClass extends PatientImageServiceImpl {
        private PatientImageServiceImpSubClass(BahmniCoreApiProperties properties) {
            super(properties);
        }
    }
}