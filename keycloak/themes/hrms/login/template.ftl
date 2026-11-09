<#macro registrationLayout bodyClass="" displayInfo=false displayMessage=true displayRequiredFields=false>
<!DOCTYPE html>
<html class="${properties.kcHtmlClass!}"<#if realm.internationalizationEnabled> lang="${locale.currentLanguageTag}"</#if>>

<head>
    <meta charset="utf-8">
    <meta http-equiv="Content-Type" content="text/html; charset=UTF-8" />
    <meta name="robots" content="noindex, nofollow">

    <#if properties.meta?has_content>
        <#list properties.meta?split(' ') as meta>
            <meta name="${meta?split('==')[0]}" content="${meta?split('==')[1]}"/>
        </#list>
    </#if>
    <title>${msg("loginTitle",(realm.displayName!''))}</title>
    <link rel="icon" href="${url.resourcesPath}/img/favicon.svg" type="image/svg+xml" />
    <#if properties.styles?has_content>
        <#list properties.styles?split(' ') as style>
            <link href="${url.resourcesPath}/${style}" rel="stylesheet" />
        </#list>
    </#if>
    <#if properties.scripts?has_content>
        <#list properties.scripts?split(' ') as script>
            <script src="${url.resourcesPath}/${script}" type="text/javascript"></script>
        </#list>
    </#if>
    <script type="importmap">
        {
            "imports": {
                "rfc4648": "${url.resourcesCommonPath}/node_modules/rfc4648/lib/rfc4648.js"
            }
        }
    </script>
    <script src="${url.resourcesPath}/js/menu-button-links.js" type="module"></script>
    <#if scripts??>
        <#list scripts as script>
            <script src="${script}" type="text/javascript"></script>
        </#list>
    </#if>
    <script type="module">
        import { checkCookiesAndSetTimer } from "${url.resourcesPath}/js/authChecker.js";

        checkCookiesAndSetTimer(
          "${url.ssoLoginInOtherTabsUrl?no_esc}"
        );
    </script>
</head>

<body class="${properties.kcBodyClass!}">
<div class="hrms-page">
  <div class="hrms-card">

    <aside class="hrms-brand">
      <div class="hrms-brand__content">
        <span class="hrms-logo">
          <svg class="hrms-logo__mark" viewBox="0 0 48 48" fill="none" aria-hidden="true">
            <path d="M24 4.5c4.9 3.6 7.4 8.1 7.4 13.6 0 5.5-2.5 10-7.4 13.6-4.9-3.6-7.4-8.1-7.4-13.6 0-5.5 2.5-10 7.4-13.6Z" fill="#1d4ed8"/>
            <path d="M32.3 16.8c4.9 1.1 8.6 4 11.2 8.6-4.2 4.3-8.9 6.3-14 6-1.1-4.8-.2-9.7 2.8-14.6Z" fill="#3b82f6"/>
            <path d="M15.7 16.8c-4.9 1.1-8.6 4-11.2 8.6 4.2 4.3 8.9 6.3 14 6 1.1-4.8.2-9.7-2.8-14.6Z" fill="#60a5fa"/>
          </svg>
          <span class="hrms-logo__text">
            <strong>${msg("hrms.brandName")}</strong>
            <small>${msg("hrms.brandTagline")}</small>
          </span>
        </span>

        <h2 class="hrms-brand__welcome">${msg("hrms.welcome")}</h2>
        <p class="hrms-brand__tagline">${msg("hrms.tagline")}</p>
        <span class="hrms-brand__accent" aria-hidden="true"></span>
      </div>

      <p class="hrms-brand__foot">&copy; <span id="hrms-year">2026</span> ${realm.displayName!msg("hrms.brandName")}. ${msg("hrms.rights")}</p>
    </aside>

    <main class="hrms-panel">
      <div class="hrms-panel__inner">

        <#if realm.internationalizationEnabled && locale.supported?size gt 1>
          <div id="kc-locale" class="hrms-locale">
            <div id="kc-locale-wrapper">
              <div id="kc-locale-dropdown" class="menu-button-links">
                <button tabindex="1" id="kc-current-locale-link" aria-label="${msg("languages")}" aria-haspopup="true" aria-expanded="false" aria-controls="language-switch1">${locale.current}</button>
                <ul role="menu" tabindex="-1" aria-labelledby="kc-current-locale-link" id="language-switch1">
                  <#assign i = 1>
                  <#list locale.supported as l>
                    <li role="none"><a role="menuitem" id="language-${i}" href="${l.url}">${l.label}</a></li>
                    <#assign i++>
                  </#list>
                </ul>
              </div>
            </div>
          </div>
        </#if>

        <#if displayMessage && message?has_content && (message.type != 'warning' || !isAppInitiatedAction??)>
          <div class="hrms-alert hrms-alert--<#if message.type = 'error'>danger<#else>${message.type}</#if>" role="alert">
            <span class="hrms-alert__text">${kcSanitize(message.summary)?no_esc}</span>
          </div>
        </#if>

        <#if !(auth?has_content && auth.showUsername() && !auth.showResetCredentials())>
          <h1 id="kc-page-title" class="hrms-title"><#nested "header"></h1>
          <#if displayRequiredFields>
            <p class="hrms-required"><span class="required">*</span> ${msg("requiredFields")}</p>
          </#if>
        <#else>
          <#nested "show-username">
          <div id="kc-username" class="hrms-attempted">
            <span id="kc-attempted-username">${auth.attemptedUsername}</span>
            <a id="reset-login" href="${url.loginRestartFlowUrl}" aria-label="${msg("restartLoginTooltip")}" title="${msg("restartLoginTooltip")}">&#8635;</a>
          </div>
          <#if displayRequiredFields>
            <p class="hrms-required"><span class="required">*</span> ${msg("requiredFields")}</p>
          </#if>
        </#if>

        <div id="kc-content">
          <div id="kc-content-wrapper">

            <#nested "form">

            <#if auth?has_content && auth.showTryAnotherWayLink()>
              <form id="kc-select-try-another-way-form" action="${url.loginAction}" method="post">
                <div class="hrms-field">
                  <input type="hidden" name="tryAnotherWay" value="on"/>
                  <a href="#" id="try-another-way"
                     onclick="document.forms['kc-select-try-another-way-form'].submit();return false;">${msg("doTryAnotherWay")}</a>
                </div>
              </form>
            </#if>

            <#nested "socialProviders">

            <#if displayInfo>
              <div id="kc-info" class="hrms-info">
                <div id="kc-info-wrapper"><#nested "info"></div>
              </div>
            </#if>
          </div>
        </div>

      </div>
    </main>
  </div>
</div>
<script>
  (function () {
    var year = document.getElementById('hrms-year');
    if (year) { year.textContent = new Date().getFullYear(); }
  })();
</script>
</body>
</html>
</#macro>
