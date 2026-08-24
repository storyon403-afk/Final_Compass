package cn.finalscompass.shared.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

/** 请求范围传输元数据的强类型访问入口 */
public final class RequestContextHolder {
  private RequestContextHolder() {}

  public static Optional<RequestContext> from(HttpServletRequest request) {
    Object value = request.getAttribute(RequestContext.REQUEST_ATTRIBUTE);
    return value instanceof RequestContext context ? Optional.of(context) : Optional.empty();
  }
}
