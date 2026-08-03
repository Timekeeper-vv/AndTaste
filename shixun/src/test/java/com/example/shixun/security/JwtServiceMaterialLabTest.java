package com.example.shixun.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceMaterialLabTest {
    private final JwtService service = new JwtService(
            new ObjectMapper(),
            "test-only-jwt-secret-that-is-long-enough-2026",
            900
    );

    @Test
    void materialLabTokenIsBoundToItsAssetAndScope() {
        String token = service.issueMaterialLabAccessToken(7L, "creative-user", "user", 42L);

        JwtService.Claims claims = service.verifyMaterialLabAccessToken(token, 42L);

        assertThat(claims.userId()).isEqualTo(7L);
        assertThat(claims.role()).isEqualTo("user");
        assertThatThrownBy(() -> service.verifyMaterialLabAccessToken(token, 43L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.verifyMediaAccessToken(token, 42L))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.verify(token))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("受限媒体令牌不能用于通用接口");
        assertThatThrownBy(() -> service.issueMaterialLabAccessToken(1L, "admin", "admin", 42L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("材质实验室仅支持C端用户");
    }

    @Test
    void ordinaryReadTokenRemainsCompatibleButCannotUploadToMaterialLab() {
        String token = service.issueMediaAccessToken(7L, "creative-user", "user", 42L);

        assertThat(service.verifyAssetReadOrMaterialLabAccessToken(token, 42L).userId()).isEqualTo(7L);
        assertThatThrownBy(() -> service.verifyMaterialLabAccessToken(token, 42L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
