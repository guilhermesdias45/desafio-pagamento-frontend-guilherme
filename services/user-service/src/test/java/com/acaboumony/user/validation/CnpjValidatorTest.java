package com.acaboumony.user.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintValidatorContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CnpjValidatorTest {

    private CnpjValidator validator;
    private ConstraintValidatorContext ctx;

    @BeforeEach
    void setUp() {
        validator = new CnpjValidator();
        ctx = mock(ConstraintValidatorContext.class);
    }

    @Test
    void deve_aceitar_cnpj_quando_14_digitos_e_dv_validos() {
        // "11222333000181" is a well-known valid CNPJ used in RFC tests
        assertThat(validator.isValid("11222333000181", ctx)).isTrue();
    }

    @Test
    void deve_rejeitar_quando_menos_de_14_digitos() {
        // CE-REG-003
        assertThat(validator.isValid("1122233300018", ctx)).isFalse();
    }

    @Test
    void deve_rejeitar_quando_mais_de_14_digitos() {
        assertThat(validator.isValid("112223330001811", ctx)).isFalse();
    }

    @Test
    void deve_rejeitar_quando_contem_letras() {
        assertThat(validator.isValid("1122233300018A", ctx)).isFalse();
    }

    @Test
    void deve_rejeitar_quando_contem_formatacao_com_pontos_e_barras() {
        // Input must already be clean (controller normalises before calling validator)
        assertThat(validator.isValid("11.222.333/0001-81", ctx)).isFalse();
    }

    @Test
    void deve_rejeitar_quando_todos_os_digitos_iguais() {
        // All-identical CNPJs pass Módulo 11 arithmetically but are invalid by convention
        assertThat(validator.isValid("11111111111111", ctx)).isFalse();
    }

    @Test
    void deve_rejeitar_quando_dv_incorretos() {
        // CE-REG-004: last two digits wrong
        assertThat(validator.isValid("11222333000199", ctx)).isFalse();
    }

    @Test
    void deve_aceitar_null_quando_role_nao_e_merchant_owner() {
        // CnpjValidator accepts null; mandatory check is @ValidRegisterRequest (task-14)
        assertThat(validator.isValid(null, ctx)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "11222333000181",  // valid
            "60701190000104",  // Itaú Unibanco — well-known valid CNPJ
    })
    void deve_aceitar_cnpjs_validos_conhecidos(String cnpj) {
        assertThat(validator.isValid(cnpj, ctx)).isTrue();
    }
}
