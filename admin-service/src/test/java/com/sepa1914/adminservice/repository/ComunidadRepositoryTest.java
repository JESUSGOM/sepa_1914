package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.TipoReparto;
import com.sepa1914.adminservice.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class ComunidadRepositoryTest {

    @Autowired
    private ComunidadRepository comunidadRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Usuario adminAlpha;
    private Usuario adminBeta;

    @BeforeEach
    void setUp() {
        // Generamos los administradores obligatorios para segmentar el perímetro
        adminAlpha = new Usuario();
        adminAlpha.setUsername("alpha_gestor");
        adminAlpha.setPassword("password123");
        entityManager.persist(adminAlpha);

        adminBeta = new Usuario();
        adminBeta.setUsername("beta_gestor");
        adminBeta.setPassword("password456");
        entityManager.persist(adminBeta);

        entityManager.flush();
    }

    @Test
    @DisplayName("1. Conteo de Seguridad: Cuenta con precisión el número de fincas asociadas a un administrador")
    void contarPorUsuario_DebeCalcularElTotalExacto() {
        // GIVEN: 2 comunidades mapeadas a Alpha y 1 a Beta
        crearComunidadPersistida("Comunidad Alpha One", "Calle de la Plata 1", "Madrid", adminAlpha);
        crearComunidadPersistida("Comunidad Alpha Two", "Avenida del Sol 40", "Madrid", adminAlpha);
        crearComunidadPersistida("Comunidad Beta Isolated", "Plaza Mayor 5", "Toledo", adminBeta);
        entityManager.flush();

        // WHEN: Ejecutamos el conteo perimetral para Alpha
        long totalAlpha = comunidadRepository.contarPorUsuario(adminAlpha.getId());

        // THEN: El contador debe ignorar por completo los registros de Beta
        assertEquals(2, totalAlpha, "El conteo de comunidades de Alpha debería ser exactamente 2.");
    }

    @Test
    @DisplayName("2. Listado Estructural: Extrae todas las comunidades de un administrador sin paginar")
    void findByAdministrador_SinPaginar_FiltraCorrectamente() {
        // GIVEN: Comunidades registradas bajo distintos gestores
        crearComunidadPersistida("Finca Malgrat 1914", "Calle Malgrat 10", "Barcelona", adminAlpha);
        crearComunidadPersistida("Finca Extranjera", "Avenida Diagonal 200", "Barcelona", adminBeta);
        entityManager.flush();

        // WHEN: Solicitamos la lista genérica para adminAlpha
        List<Comunidad> resultado = comunidadRepository.findByAdministrador(adminAlpha);

        // THEN: Verificamos que no exista filtración de datos de Beta hacia Alpha
        assertNotNull(resultado);
        assertEquals(1, resultado.size());
        assertEquals("Finca Malgrat 1914", resultado.get(0).getNombre());
    }

    @Test
    @DisplayName("3. Listado Paginado: Segmenta los resultados respetando el tamaño de página solicitado")
    void findByAdministrador_Paginado_RetornaTrozoExacto() {
        // GIVEN: 3 comunidades para el mismo administrador
        crearComunidadPersistida("Comunidad 1", "Direccion 1", "Poblacion 1", adminAlpha);
        crearComunidadPersistida("Comunidad 2", "Direccion 2", "Poblacion 2", adminAlpha);
        crearComunidadPersistida("Comunidad 3", "Direccion 3", "Poblacion 3", adminAlpha);
        entityManager.flush();

        // Pedimos la primera página (índice 0) con un tamaño máximo de 2 elementos
        Pageable paginaLimitada = PageRequest.of(0, 2);

        // WHEN
        Page<Comunidad> paginaResultado = comunidadRepository.findByAdministrador(adminAlpha, paginaLimitada);

        // THEN: Comprobamos que reporte el total real pero entregue solo el trozo acotado
        assertNotNull(paginaResultado);
        assertEquals(3, paginaResultado.getTotalElements(), "El total de elementos en la base de datos es 3.");
        assertEquals(2, paginaResultado.getContent().size(), "El trozo de página actual solo debe contener 2.");
    }

    @Test
    @DisplayName("4. Buscador Predictivo: Filtra por texto (LIKE) de forma insensible a mayúsculas en Nombre/Dirección/Población")
    void buscarPorAdminYTexto_DebeCruzarCamposCorrectamente() {
        // GIVEN: Un ecosistema de comunidades con patrones de texto específicos y aislados
        crearComunidadPersistida("Residencial Los Olivos", "Avenida de Andalucía 5", "Getafe", adminAlpha);
        crearComunidadPersistida("Urb. El Sabinar", "Calle Arbusto Silvestre 12", "Almería", adminAlpha); // Desambiguado aquí
        crearComunidadPersistida("Finca Central", "Paseo de la Estación 2", "Olivos del Rey", adminAlpha);
        crearComunidadPersistida("Finca Los Olivos Intruso", "Ruta 66", "Zaragoza", adminBeta); // Pertenece a Beta
        entityManager.flush();

        Pageable paginacionCompleta = PageRequest.of(0, 10);

        // WHEN & THEN: Caso A - Coincidencia en el NOMBRE ("olivos")
        Page<Comunidad> porNombre = comunidadRepository.buscarPorAdminYTexto(adminAlpha, "olivos", paginacionCompleta);
        assertEquals(1, porNombre.getContent().size(), "Debería encontrar solo la comunidad con 'Olivos' en el nombre.");
        assertEquals("Residencial Los Olivos", porNombre.getContent().get(0).getNombre());

        // WHEN & THEN: Caso B - Coincidencia en la DIRECCIÓN ("Andalucía")
        Page<Comunidad> porDireccion = comunidadRepository.buscarPorAdminYTexto(adminAlpha, "Andalucía", paginacionCompleta);
        assertEquals(1, porDireccion.getContent().size(), "Debería encontrar la comunidad por la dirección.");
        assertEquals("Residencial Los Olivos", porDireccion.getContent().get(0).getNombre());

        // WHEN & THEN: Caso C - Coincidencia en la POBLACIÓN ("rey")
        Page<Comunidad> porPoblacion = comunidadRepository.buscarPorAdminYTexto(adminAlpha, "rey", paginacionCompleta);
        assertEquals(1, porPoblacion.getContent().size(), "Debería encontrar la comunidad por la población.");
        assertEquals("Finca Central", porPoblacion.getContent().get(0).getNombre());
    }

    /**
     * Factoría interna para instanciar comunidades válidas según las restricciones NOT NULL analizadas
     */
    private void crearComunidadPersistida(String nombre, String direccion, String poblacion, Usuario administrador) {
        Comunidad c = new Comunidad();
        c.setNombre(nombre);
        c.setDireccion(direccion);
        c.setPoblacion(poblacion);
        c.setTipoReparto(TipoReparto.PARTES_IGUALES);
        c.setAdministrador(administrador);
        entityManager.persist(c);
    }
}