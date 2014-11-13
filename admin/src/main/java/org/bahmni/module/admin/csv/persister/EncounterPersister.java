package org.bahmni.module.admin.csv.persister;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.bahmni.csv.EntityPersister;
import org.bahmni.csv.RowResult;
import org.bahmni.module.admin.csv.models.MultipleEncounterRow;
import org.bahmni.module.admin.csv.service.PatientMatchService;
import org.bahmni.module.admin.encounter.BahmniEncounterTransactionImportService;
import org.bahmni.module.admin.observation.DiagnosisMapper;
import org.bahmni.module.admin.observation.ObservationMapper;
import org.bahmni.module.admin.retrospectiveEncounter.service.RetrospectiveEncounterTransactionService;
import org.openmrs.Patient;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.VisitService;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.UserContext;
import org.openmrs.module.bahmniemrapi.encountertransaction.contract.BahmniEncounterTransaction;
import org.openmrs.module.bahmniemrapi.encountertransaction.service.BahmniEncounterTransactionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class EncounterPersister implements EntityPersister<MultipleEncounterRow> {
    @Autowired
    private PatientMatchService patientMatchService;
    @Autowired
    private BahmniEncounterTransactionService bahmniEncounterTransactionService;
    @Autowired
    private ConceptService conceptService;
    @Autowired
    private EncounterService encounterService;
    @Autowired
    private VisitService visitService;

    private UserContext userContext;
    private String patientMatchingAlgorithmClassName;
    private boolean shouldMatchExactPatientId;
    protected DiagnosisMapper diagnosisMapper;
    private ObservationMapper observationMapper;

    private static final Logger log = Logger.getLogger(EncounterPersister.class);

    public void init(UserContext userContext, String patientMatchingAlgorithmClassName, boolean shouldMatchExactPatientId) {
        this.userContext = userContext;
        this.patientMatchingAlgorithmClassName = patientMatchingAlgorithmClassName;
        this.shouldMatchExactPatientId = shouldMatchExactPatientId;

        // Diagnosis Service caches the diagnoses concept. Better if there is one instance of it for the one file import.
        diagnosisMapper = new DiagnosisMapper(conceptService);
        observationMapper = new ObservationMapper(conceptService);
    }

    @Override
    public RowResult<MultipleEncounterRow> validate(MultipleEncounterRow multipleEncounterRow) {
        return new RowResult<>(multipleEncounterRow);
    }

    @Override
    public RowResult<MultipleEncounterRow> persist(MultipleEncounterRow multipleEncounterRow) {
        // This validation is needed as patientservice get returns all patients for empty patient identifier
        if (StringUtils.isEmpty(multipleEncounterRow.patientIdentifier)) {
            return noMatchingPatients(multipleEncounterRow);
        }

        try {
            Context.openSession();
            Context.setUserContext(userContext);

            Patient patient = patientMatchService.getPatient(patientMatchingAlgorithmClassName, multipleEncounterRow.patientAttributes,
                    multipleEncounterRow.patientIdentifier, shouldMatchExactPatientId);
            if (patient == null) {
                return noMatchingPatients(multipleEncounterRow);
            }

            BahmniEncounterTransactionImportService encounterTransactionImportService =
                    new BahmniEncounterTransactionImportService(encounterService, observationMapper, diagnosisMapper);
            List<BahmniEncounterTransaction> bahmniEncounterTransactions = encounterTransactionImportService.getBahmniEncounterTransaction(multipleEncounterRow, patient);

            RetrospectiveEncounterTransactionService retrospectiveEncounterTransactionService =
                    new RetrospectiveEncounterTransactionService(bahmniEncounterTransactionService, visitService);

            for (BahmniEncounterTransaction bahmniEncounterTransaction : bahmniEncounterTransactions) {
                retrospectiveEncounterTransactionService.save(bahmniEncounterTransaction, patient, multipleEncounterRow.getVisitStartDate(), multipleEncounterRow.getVisitEndDate());
            }

            return new RowResult<>(multipleEncounterRow);
        } catch (Exception e) {
            log.error(e);
            Context.clearSession();
            return new RowResult<>(multipleEncounterRow, e);
        } finally {
            Context.flushSession();
            Context.closeSession();
        }
    }

    private RowResult<MultipleEncounterRow> noMatchingPatients(MultipleEncounterRow multipleEncounterRow) {
        return new RowResult<>(multipleEncounterRow, "No matching patients found with ID:'" + multipleEncounterRow.patientIdentifier + "'");
    }
}