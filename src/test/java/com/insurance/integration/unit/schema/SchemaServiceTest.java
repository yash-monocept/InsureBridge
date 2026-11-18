package com.insurance.integration.unit.schema;

import com.insurance.integration.common.exception.DuplicateResourceException;
import com.insurance.integration.common.exception.ResourceNotFoundException;
import com.insurance.integration.schema.dto.DocumentRuleCreateRequest;
import com.insurance.integration.schema.dto.DocumentRuleDTO;
import com.insurance.integration.schema.dto.FieldRuleCreateRequest;
import com.insurance.integration.schema.dto.FieldRuleDTO;
import com.insurance.integration.schema.dto.SchemaCreateRequest;
import com.insurance.integration.schema.dto.SchemaDTO;
import com.insurance.integration.schema.dto.SchemaUpdateRequest;
import com.insurance.integration.schema.entity.ProductSchema;
import com.insurance.integration.schema.repository.DocumentRuleRepository;
import com.insurance.integration.schema.repository.FieldRuleRepository;
import com.insurance.integration.schema.repository.ProductSchemaRepository;
import com.insurance.integration.schema.service.SchemaService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Module 4 {@link SchemaService}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SchemaService Unit Tests")
class SchemaServiceTest {

    @Mock
    private ProductSchemaRepository schemaRepository;

    @Mock
    private FieldRuleRepository fieldRuleRepository;

    @Mock
    private DocumentRuleRepository documentRuleRepository;

    @InjectMocks
    private SchemaService schemaService;

    private static UUID randomId() {
        return UUID.randomUUID();
    }

    private ProductSchema schema(String version, boolean active) {
        return ProductSchema.builder()
                .productType("MOTOR_INSURANCE")
                .schemaVersion(version)
                .schemaName("Motor Schema " + version)
                .active(active)
                .build();
    }

    // ─── createSchema ───────────────────────────────────────────────────

    @Test
    @DisplayName("createSchema persists and returns the created schema")
    void createSchema_persistsAndReturns() {
        SchemaCreateRequest request = SchemaCreateRequest.builder()
                .productType("MOTOR_INSURANCE").schemaVersion("1.0")
                .schemaName("Motor Schema v1.0").active(true).build();

        when(schemaRepository.findWithFilters(null, null, "MOTOR_INSURANCE", null, null, PageRequest.of(0, 1)))
                .thenReturn(new PageImpl<>(List.of()));
        when(schemaRepository.save(any(ProductSchema.class))).thenAnswer(inv -> inv.getArgument(0));

        SchemaDTO result = schemaService.createSchema(request);

        assertThat(result.getProductType()).isEqualTo("MOTOR_INSURANCE");
        assertThat(result.getSchemaVersion()).isEqualTo("1.0");
        assertThat(result.getSchemaName()).isEqualTo("Motor Schema v1.0");
        assertThat(result.isActive()).isTrue();
        verify(schemaRepository).save(any(ProductSchema.class));
    }

    @Test
    @DisplayName("createSchema rejects duplicate version for same product")
    void createSchema_duplicateVersion_rejects() {
        SchemaCreateRequest request = SchemaCreateRequest.builder()
                .productType("MOTOR_INSURANCE").schemaVersion("1.0")
                .schemaName("Motor Schema v1.0").active(true).build();

        when(schemaRepository.findWithFilters(null, null, "MOTOR_INSURANCE", null, null, PageRequest.of(0, 1)))
                .thenReturn(new PageImpl<>(List.of(schema("1.0", true))));

        assertThatThrownBy(() -> schemaService.createSchema(request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    // ─── getSchema ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getSchema returns the schema when active")
    void getSchema_returnsActive() {
        UUID id = randomId();
        ProductSchema s = schema("1.0", true);
        when(schemaRepository.findByIdAndActive(id, true)).thenReturn(Optional.of(s));

        SchemaDTO result = schemaService.getSchema(id);

        assertThat(result.getSchemaVersion()).isEqualTo("1.0");
    }

    @Test
    @DisplayName("getSchema throws when not found or inactive")
    void getSchema_notFound_throws() {
        when(schemaRepository.findByIdAndActive(any(), anyBoolean())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> schemaService.getSchema(randomId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── listSchemas ────────────────────────────────────────────────────

    @Test
    @DisplayName("listSchemas returns mapped page")
    void listSchemas_mapsPage() {
        ProductSchema s = schema("1.0", true);
        Page<ProductSchema> page = new PageImpl<>(List.of(s), PageRequest.of(0, 10), 1);
        when(schemaRepository.findWithFilters(any(), any(), any(), any(), any(), any()))
                .thenReturn(page);

        Page<SchemaDTO> result = schemaService.listSchemas(null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).getSchemaVersion()).isEqualTo("1.0");
    }

    // ─── updateSchema ───────────────────────────────────────────────────

    @Test
    @DisplayName("updateSchema updates name and active flag")
    void updateSchema_updatesFields() {
        UUID id = randomId();
        ProductSchema s = schema("1.0", true);
        when(schemaRepository.findByIdAndActive(id, true)).thenReturn(Optional.of(s));
        when(schemaRepository.save(any(ProductSchema.class))).thenReturn(s);

        SchemaUpdateRequest request = SchemaUpdateRequest.builder()
                .schemaName("Renamed Schema").active(false).build();

        SchemaDTO result = schemaService.updateSchema(id, request);

        assertThat(result.getSchemaName()).isEqualTo("Renamed Schema");
        assertThat(result.isActive()).isFalse();
    }

    @Test
    @DisplayName("updateSchema throws when not found")
    void updateSchema_notFound_throws() {
        when(schemaRepository.findByIdAndActive(any(), anyBoolean())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> schemaService.updateSchema(randomId(),
                SchemaUpdateRequest.builder().build()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── field rules ────────────────────────────────────────────────────

    @Test
    @DisplayName("createFieldRule persists and returns the rule")
    void createFieldRule_persistsAndReturns() {
        UUID schemaId = randomId();
        ProductSchema s = schema("1.0", true);
        when(schemaRepository.findByIdAndActive(schemaId, true)).thenReturn(Optional.of(s));
        when(fieldRuleRepository.existsBySchemaIdAndFieldPath(schemaId, "customer.name")).thenReturn(false);
        when(fieldRuleRepository.save(any())).thenAnswer(inv -> {
            com.insurance.integration.schema.entity.FieldRule r = inv.getArgument(0);
            return com.insurance.integration.schema.entity.FieldRule.builder()
                    .schemaId(r.getSchemaId()).fieldPath(r.getFieldPath())
                    .displayName(r.getDisplayName()).required(r.isRequired()).dataType(r.getDataType())
                    .build();
        });

        FieldRuleCreateRequest request = FieldRuleCreateRequest.builder()
                .fieldPath("customer.name").displayName("Customer Name").required(true)
                .dataType(com.insurance.integration.common.enums.FieldDataType.STRING).build();

        FieldRuleDTO result = schemaService.createFieldRule(schemaId, request);

        assertThat(result.getFieldPath()).isEqualTo("customer.name");
        assertThat(result.getDisplayName()).isEqualTo("Customer Name");
        assertThat(result.isRequired()).isTrue();
        assertThat(result.getDataType()).isEqualTo(com.insurance.integration.common.enums.FieldDataType.STRING);
        verify(fieldRuleRepository).save(any());
    }

    @Test
    @DisplayName("createFieldRule rejects duplicate field path")
    void createFieldRule_duplicatePath_rejects() {
        UUID schemaId = randomId();
        ProductSchema s = schema("1.0", true);
        when(schemaRepository.findByIdAndActive(schemaId, true)).thenReturn(Optional.of(s));
        when(fieldRuleRepository.existsBySchemaIdAndFieldPath(schemaId, "customer.name")).thenReturn(true);

        FieldRuleCreateRequest request = FieldRuleCreateRequest.builder()
                .fieldPath("customer.name").displayName("Customer Name").required(true)
                .dataType(com.insurance.integration.common.enums.FieldDataType.STRING).build();

        assertThatThrownBy(() -> schemaService.createFieldRule(schemaId, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("listFieldRules returns rules for a schema")
    void listFieldRules_returnsRules() {
        UUID schemaId = randomId();
        com.insurance.integration.schema.entity.FieldRule r =
                com.insurance.integration.schema.entity.FieldRule.builder()
                        .schemaId(schemaId).fieldPath("customer.name")
                        .displayName("Customer Name").required(true)
                        .dataType(com.insurance.integration.common.enums.FieldDataType.STRING).build();
        when(schemaRepository.existsById(schemaId)).thenReturn(true);
        when(fieldRuleRepository.findBySchemaId(schemaId)).thenReturn(List.of(r));

        List<FieldRuleDTO> result = schemaService.listFieldRules(schemaId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFieldPath()).isEqualTo("customer.name");
    }

    // ─── document rules ─────────────────────────────────────────────────

    @Test
    @DisplayName("createDocumentRule persists and returns the rule")
    void createDocumentRule_persistsAndReturns() {
        UUID schemaId = randomId();
        ProductSchema s = schema("1.0", true);
        when(schemaRepository.findByIdAndActive(schemaId, true)).thenReturn(Optional.of(s));
        when(documentRuleRepository.existsBySchemaIdAndDocumentType(schemaId, "IDENTITY_PROOF")).thenReturn(false);
        when(documentRuleRepository.save(any())).thenAnswer(inv -> {
            com.insurance.integration.schema.entity.DocumentRule dr = inv.getArgument(0);
            return com.insurance.integration.schema.entity.DocumentRule.builder()
                    .schemaId(dr.getSchemaId()).documentType(dr.getDocumentType())
                    .required(dr.isRequired()).allowedMimeTypes(dr.getAllowedMimeTypes())
                    .maxSizeMb(dr.getMaxSizeMb()).description(dr.getDescription()).build();
        });

        DocumentRuleCreateRequest request = DocumentRuleCreateRequest.builder()
                .documentType("IDENTITY_PROOF").required(true)
                .allowedMimeTypes("[\"application/pdf\"]").maxSizeMb(10)
                .description("Identity proof").build();

        DocumentRuleDTO result = schemaService.createDocumentRule(schemaId, request);

        assertThat(result.getDocumentType()).isEqualTo("IDENTITY_PROOF");
        verify(documentRuleRepository).save(any());
    }

    @Test
    @DisplayName("createDocumentRule rejects duplicate document type")
    void createDocumentRule_duplicateType_rejects() {
        UUID schemaId = randomId();
        ProductSchema s = schema("1.0", true);
        when(schemaRepository.findByIdAndActive(schemaId, true)).thenReturn(Optional.of(s));
        when(documentRuleRepository.existsBySchemaIdAndDocumentType(schemaId, "IDENTITY_PROOF")).thenReturn(true);

        DocumentRuleCreateRequest request = DocumentRuleCreateRequest.builder()
                .documentType("IDENTITY_PROOF").required(true)
                .allowedMimeTypes("[\"application/pdf\"]").maxSizeMb(10).build();

        assertThatThrownBy(() -> schemaService.createDocumentRule(schemaId, request))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("listDocumentRules returns rules for a schema")
    void listDocumentRules_returnsRules() {
        UUID schemaId = randomId();
        com.insurance.integration.schema.entity.DocumentRule dr =
                com.insurance.integration.schema.entity.DocumentRule.builder()
                        .schemaId(schemaId).documentType("IDENTITY_PROOF")
                        .required(true).allowedMimeTypes("[\"application/pdf\"]").maxSizeMb(10)
                        .description("Identity proof").build();
        when(schemaRepository.existsById(schemaId)).thenReturn(true);
        when(documentRuleRepository.findBySchemaId(schemaId)).thenReturn(List.of(dr));

        List<DocumentRuleDTO> result = schemaService.listDocumentRules(schemaId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getDocumentType()).isEqualTo("IDENTITY_PROOF");
    }

    @Test
    @DisplayName("listFieldRules throws when schema missing")
    void listFieldRules_schemaMissing_throws() {
        when(schemaRepository.existsById(any())).thenReturn(false);
        assertThatThrownBy(() -> schemaService.listFieldRules(randomId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
