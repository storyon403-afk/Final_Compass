package cn.finalscompass.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import cn.finalscompass.service.ActionRateLimitService;
import cn.finalscompass.service.AuthService;
import cn.finalscompass.shared.security.AuthorizationPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

class SiteMessageServiceTest {
  private final JdbcClient jdbc = mock(JdbcClient.class, RETURNS_DEEP_STUBS);
  private final ActionRateLimitService limits = mock(ActionRateLimitService.class);
  private final SiteMessageService service = new SiteMessageService(jdbc, new AuthorizationPolicy(), limits);
  private final AuthService.CurrentUser user =
      new AuthService.CurrentUser(7, "user", "User", "USER", "hash", "token", false);
  private final AuthService.CurrentUser admin =
      new AuthService.CurrentUser(1, "admin", "Admin", "ADMIN", "hash", "token", false);

  @Test
  void contactAdminConsumesRateLimitAndReportsMissingAdmin() {
    when(jdbc.sql(anyString()).param(anyString(), any()).param(anyString(), any()).param(anyString(), any()).update())
        .thenReturn(0);

    assertThatThrownBy(() -> service.contactAdmin(user, new SiteMessageService.ContactInput("主题", "正文")))
        .hasMessageContaining("没有可联系的管理员");
    verify(limits).contactAdmin(7);
  }

  @Test
  void broadcastConsumesDedicatedAdminLimit() {
    when(jdbc.sql(anyString()).param(anyString(), any()).param(anyString(), any()).param(anyString(), any()).param(anyString(), any()).update())
        .thenReturn(3);

    var result = service.adminSend(admin, new SiteMessageService.AdminSendInput(null, "公告", "正文", "/cet"));

    assertThat(result).containsEntry("sent", 3);
    verify(limits).adminBroadcast(1);
  }

  @Test
  void ordinaryUserCannotSendAdministrativeMessage() {
    assertThatThrownBy(() -> service.adminSend(user, new SiteMessageService.AdminSendInput(null, "公告", "正文", null)))
        .hasMessageContaining("只有管理员");
    verifyNoInteractions(limits);
  }
}
