(function loadOidc4vcAdminExtension() {
  const scriptId = "oidc4vc-admin-extension-script";
  if (document.getElementById(scriptId)) {
    return;
  }

  const script = document.createElement("script");
  script.id = scriptId;
  script.type = "text/javascript";
  script.src = `${window.location.origin}/resources/oidc4vc-admin-ui/oidc4vc-admin/js/oidc4vc-client-tab.js`;
  script.async = true;

  document.head.appendChild(script);
})();
