package com.cbo.players.unit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class TestingProfileUnitTest {

    @Test
    @DisplayName("H2 driver no debe estar presente en el classpath")
    void h2DriverMustNotBePresentOnClasspath() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.h2.Driver"),
                "El driver de H2 no debe existir en el classpath del proyecto");
    }

    @Test
    @DisplayName("PostgreSQL driver debe estar disponible en el classpath")
    void postgresDriverMustBePresentOnClasspath() {
        assertDoesNotThrow(() -> Class.forName("org.postgresql.Driver"),
                "El driver de PostgreSQL debe estar disponible en el classpath");
    }

    @Test
    @DisplayName("application-unit.yml debe excluir auto-configuraciones de base de datos y redis para garantizar aislamiento")
    void unitProfileConfigurationMustExcludeDatabaseAndRedisAutoconfiguration() {
        Yaml yaml = new Yaml();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application-unit.yml")) {
            assertNotNull(in, "El archivo application-unit.yml debe existir en test/resources");
            Map<String, Object> data = yaml.load(in);
            assertNotNull(data, "El archivo application-unit.yml no debe estar vacío");

            @SuppressWarnings("unchecked")
            Map<String, Object> spring = (Map<String, Object>) data.get("spring");
            assertNotNull(spring, "Debe contener sección 'spring'");

            @SuppressWarnings("unchecked")
            Map<String, Object> autoconfigure = (Map<String, Object>) spring.get("autoconfigure");
            assertNotNull(autoconfigure, "Debe contener sección 'spring.autoconfigure'");

            @SuppressWarnings("unchecked")
            List<String> excludes = (List<String>) autoconfigure.get("exclude");
            assertNotNull(excludes, "Debe definir lista de auto-configuraciones excluidas");

            assertTrue(excludes.contains("org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"),
                    "Debe excluir DataSourceAutoConfiguration");
            assertTrue(excludes.contains("org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration"),
                    "Debe excluir HibernateJpaAutoConfiguration");
            assertTrue(excludes.contains("org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration"),
                    "Debe excluir RedisAutoConfiguration");
        } catch (Exception e) {
            fail("Error al leer application-unit.yml: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("application-unit.yml no debe configurar JDBC URLs de H2 o HSQLDB")
    void unitProfileMustNotContainH2OrHsqlUrls() {
        Yaml yaml = new Yaml();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("application-unit.yml")) {
            assertNotNull(in);
            Map<String, Object> data = yaml.load(in);
            @SuppressWarnings("unchecked")
            Map<String, Object> spring = (Map<String, Object>) data.get("spring");
            assertNotNull(spring);
            assertNull(spring.get("datasource"), "El perfil unit no debe configurar datasource real");
        } catch (Exception e) {
            fail("Error al validar configuración de datasource: " + e.getMessage());
        }
    }
}
