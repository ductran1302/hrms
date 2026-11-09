<#import "template.ftl" as layout>
<@layout.registrationLayout displayMessage=!messagesPerField.existsError('username','password') displayInfo=realm.password && realm.registrationAllowed && !registrationDisabled??; section>
    <#if section = "header">
        ${msg("hrms.signIn")}
    <#elseif section = "form">
        <p class="hrms-subtitle">${msg("hrms.subtitle")}</p>
        <div id="kc-form">
          <div id="kc-form-wrapper">
            <#if realm.password>
                <form id="kc-form-login" onsubmit="login.disabled = true; return true;" action="${url.loginAction}" method="post">
                    <#if !usernameHidden??>
                        <div class="hrms-field">
                            <label for="username" class="hrms-sr-only"><#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if></label>

                            <input tabindex="2" id="username" class="hrms-input" name="username" value="${(login.username!'')}" type="text" autofocus autocomplete="username" autocapitalize="none" spellcheck="false"
                                   placeholder="<#if !realm.loginWithEmailAllowed>${msg("username")}<#elseif !realm.registrationEmailAsUsername>${msg("usernameOrEmail")}<#else>${msg("email")}</#if>"
                                   aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"
                            />

                            <#if messagesPerField.existsError('username','password')>
                                <span id="input-error" class="hrms-error" aria-live="polite">
                                        ${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}
                                </span>
                            </#if>
                        </div>
                    </#if>

                    <div class="hrms-field">
                        <label for="password" class="hrms-sr-only">${msg("password")}</label>

                        <div class="hrms-input-group">
                            <input tabindex="3" id="password" class="hrms-input" name="password" type="password" autocomplete="current-password"
                                   placeholder="${msg("password")}"
                                   aria-invalid="<#if messagesPerField.existsError('username','password')>true</#if>"
                            />
                            <button class="hrms-eye" type="button" id="hrms-eye" aria-label="${msg("showPassword")}" aria-controls="password" tabindex="4">
                                <svg class="hrms-eye__open" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                                    <path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z"/>
                                    <circle cx="12" cy="12" r="3.2"/>
                                </svg>
                                <svg class="hrms-eye__off" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                                    <path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z"/>
                                    <circle cx="12" cy="12" r="3.2"/>
                                    <path d="M4 20 20 4"/>
                                </svg>
                            </button>
                        </div>

                        <#if usernameHidden?? && messagesPerField.existsError('username','password')>
                            <span id="input-error" class="hrms-error" aria-live="polite">
                                    ${kcSanitize(messagesPerField.getFirstError('username','password'))?no_esc}
                            </span>
                        </#if>
                    </div>

                    <label class="hrms-check">
                        <input tabindex="5" class="hrms-check__box" type="checkbox" aria-label="${msg("hrms.rememberMe")}">
                        <span>${msg("hrms.rememberMe")}</span>
                    </label>

                    <div id="kc-form-buttons" class="hrms-submit">
                        <input type="hidden" id="id-hidden-input" name="credentialId" <#if auth.selectedCredential?has_content>value="${auth.selectedCredential}"</#if>/>
                        <input tabindex="7" class="hrms-btn" name="login" id="kc-login" type="submit" value="${msg("hrms.signIn")}"/>
                        <svg class="hrms-submit__arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
                            <path d="M4 12h15"/>
                            <path d="m13 6 6 6-6 6"/>
                        </svg>
                    </div>
                </form>
            </#if>
            </div>
        </div>

        <div class="hrms-divider" aria-hidden="true"></div>
        <p class="hrms-help">
            <span class="hrms-help__icon" aria-hidden="true">?</span>
            <span>${msg("hrms.needHelp")} <a href="mailto:it-support@hrms.vn">${msg("hrms.contactSupport")}</a></span>
        </p>

        <script>
            (function () {
                var button = document.getElementById('hrms-eye');
                var input = document.getElementById('password');
                if (!button || !input) { return; }
                button.addEventListener('click', function () {
                    var show = input.type === 'password';
                    input.type = show ? 'text' : 'password';
                    button.classList.toggle('is-visible', show);
                    button.setAttribute('aria-label', show ? '${msg("hidePassword")?js_string}' : '${msg("showPassword")?js_string}');
                    input.focus();
                });
            })();
        </script>
    <#elseif section = "info" >
        <#if realm.password && realm.registrationAllowed && !registrationDisabled??>
            <div id="kc-registration-container">
                <div id="kc-registration">
                    <span>${msg("noAccount")} <a tabindex="8" href="${url.registrationUrl}">${msg("doRegister")}</a></span>
                </div>
            </div>
        </#if>
    <#elseif section = "socialProviders" >
        <#if realm.password && social?? && social.providers?has_content>
            <div id="kc-social-providers" class="hrms-social">
                <hr/>
                <h2>${msg("identity-provider-login-label")}</h2>

                <ul class="<#if social.providers?size gt 3>hrms-social__grid</#if>">
                    <#list social.providers as p>
                        <li>
                            <a id="social-${p.alias}" class="hrms-social__item" type="button" href="${p.loginUrl}">
                                <span>${p.displayName!}</span>
                            </a>
                        </li>
                    </#list>
                </ul>
            </div>
        </#if>
    </#if>

</@layout.registrationLayout>
