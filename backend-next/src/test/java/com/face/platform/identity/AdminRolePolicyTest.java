package com.face.platform.identity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminRolePolicyTest {

    @Test
    void exposesExactlyTwoAdministratorRoles() {
        assertThat(AdminRolePolicy.normalize("admin")).isEqualTo("ADMIN");
        assertThat(AdminRolePolicy.normalize("SUPER_ADMIN")).isEqualTo("SUPER_ADMIN");
        assertThatThrownBy(() -> AdminRolePolicy.normalize("MANAGER"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(AdminRolePolicy.visibleRoles()).containsExactly("SUPER_ADMIN", "ADMIN");
    }
}
