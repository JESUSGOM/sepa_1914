package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.LicenciaMaestra;
import com.sepa1914.adminservice.repository.LicenciaMaestraRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LicenseServiceTest {

    @Mock
    private LicenciaMaestraRepository licenciaMaestraRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private LicenseService licenseService;

    @BeforeEach
    void resetGtiCache() throws Exception {
        // Forzamos el estado inicial limpio en la memoria estática de la caché antes de cada test
        Field cacheValidaField = LicenseService.class.getDeclaredField("cacheValida");
        cacheValidaField.setAccessible(true);
        cacheValidaField.set(null, true);

        Field fechaProximaField = LicenseService.class.getDeclaredField("fechaProximaValidacion");
        fechaProximaField.setAccessible(true);
        fechaProximaField.set(null, null);

        // Inyección manual del RestTemplate mockeado
        Field restTemplateField = LicenseService.class.getDeclaredField("restTemplate");
        restTemplateField.setAccessible(true);
        restTemplateField.set(licenseService, restTemplate);
    }

    @Test
    @DisplayName("1. Caché GTI Instantánea: Retorna verdadero si no ha expirado el tiempo de caché")
    void validarLicencia_RetornaTruePorDefectoInmediatamente() throws Exception {
        // GIVEN: Forzamos de forma determinista que la caché sea válida y que la fecha de validación futura sea lejana
        Field cacheValidaField = LicenseService.class.getDeclaredField("cacheValida");
        cacheValidaField.setAccessible(true);
        cacheValidaField.set(null, true);

        Field fechaProximaField = LicenseService.class.getDeclaredField("fechaProximaValidacion");
        fechaProximaField.setAccessible(true);
        fechaProximaField.set(null, LocalDateTime.now().plusDays(1));

        // WHEN
        boolean resultado = licenseService.validarLicencia();

        // THEN: Debería responder true inmediatamente por hit de caché síncrona sin activar hilos
        assertTrue(resultado, "El acceso por caché estática vigente debe ser inmediato y exitoso.");
    }

    @Test
    @DisplayName("2. Validación Local: Licencia activa encontrada en la Base de Datos local")
    void ejecutarValidacionEnSegundoPlano_CasoExitoLocal() {
        String hidLocal = licenseService.getEquipoID();
        LicenciaMaestra licenciaMock = new LicenciaMaestra();
        licenciaMock.setHardwareId(hidLocal);

        when(licenciaMaestraRepository.findByHardwareIdAndActivoTrue(hidLocal)).thenReturn(Optional.of(licenciaMock));

        // WHEN
        licenseService.ejecutarValidacionEnSegundoPlano();

        // THEN
        verify(licenciaMaestraRepository, times(1)).findByHardwareIdAndActivoTrue(hidLocal);
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("3. Validación Remota exitosa: El servidor central confirma la licencia")
    void ejecutarValidacionEnSegundoPlano_CasoExitoRemoto() {
        String hidLocal = licenseService.getEquipoID();
        when(licenciaMaestraRepository.findByHardwareIdAndActivoTrue(hidLocal)).thenReturn(Optional.empty());

        Map<String, Object> responseMock = new HashMap<>();
        responseMock.put("activo", true);

        String urlEsperada = "https://jfgb.es/sepa/validar_licencia.php?hid=" + hidLocal;
        when(restTemplate.getForObject(urlEsperada, Map.class)).thenReturn(responseMock);

        // WHEN
        licenseService.ejecutarValidacionEnSegundoPlano();

        // THEN
        verify(restTemplate, times(1)).getForObject(urlEsperada, Map.class);
    }

    @Test
    @DisplayName("4. Bloqueo por Licencia Inactiva: Servidor central revoca el acceso")
    void ejecutarValidacionEnSegundoPlano_CasoLicenciaInactiva() {
        String hidLocal = licenseService.getEquipoID();
        when(licenciaMaestraRepository.findByHardwareIdAndActivoTrue(hidLocal)).thenReturn(Optional.empty());

        Map<String, Object> responseMock = new HashMap<>();
        responseMock.put("activo", false);

        String urlEsperada = "https://jfgb.es/sepa/validar_licencia.php?hid=" + hidLocal;
        when(restTemplate.getForObject(urlEsperada, Map.class)).thenReturn(responseMock);

        // WHEN
        licenseService.ejecutarValidacionEnSegundoPlano();

        // THEN
        verify(restTemplate, times(1)).getForObject(urlEsperada, Map.class);
    }

    @Test
    @DisplayName("5. Resiliencia de Red: Si jfgb.es se cae, la App sigue funcionando")
    void ejecutarValidacionEnSegundoPlano_ResilienteACaidasDeInternet() {
        String hidLocal = licenseService.getEquipoID();
        when(licenciaMaestraRepository.findByHardwareIdAndActivoTrue(hidLocal)).thenReturn(Optional.empty());

        String urlEsperada = "https://jfgb.es/sepa/validar_licencia.php?hid=" + hidLocal;
        when(restTemplate.getForObject(urlEsperada, Map.class)).thenThrow(new RuntimeException("Timeout de red"));

        // WHEN & THEN
        assertDoesNotThrow(() -> licenseService.ejecutarValidacionEnSegundoPlano());
    }
}