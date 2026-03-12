/**
 * Lightweight React-like helpers so we can render a component without depending on the full React runtime.
 * This enables the bundle to run even when the upstream admin console does not expose React globally.
 */
const MiniReact = (() => {
  const createElement = (type, props, ...children) => ({
    type,
    props: props || {},
    children: children.flat(),
  });

  const renderElement = (element) => {
    if (element === null || element === undefined || typeof element === "boolean") {
      return document.createComment("empty");
    }

    if (typeof element === "string" || typeof element === "number") {
      return document.createTextNode(String(element));
    }

    if (typeof element.type === "function") {
      return renderElement(element.type({ ...(element.props || {}), children: element.children }));
    }

    const domNode = document.createElement(element.type);
    const { children, ...otherProps } = element.props || {};

    Object.entries(otherProps).forEach(([key, value]) => {
      if (key === "className") {
        domNode.setAttribute("class", value);
      } else if (key.startsWith("on") && typeof value === "function") {
        domNode.addEventListener(key.substring(2).toLowerCase(), value);
      } else if (value !== false && value !== null && value !== undefined) {
        domNode.setAttribute(key, value === true ? "" : value);
      }
    });

    (element.children || []).forEach((child) => {
      domNode.appendChild(renderElement(child));
    });

    return domNode;
  };

  const render = (element, container) => {
    container.replaceChildren(renderElement(element));
  };

  return { createElement, render };
})();

const isClientDetailsRoute = () => {
  const hash = window.location.hash || "";
  return hash.includes("/clients/") && !hash.includes("/client-scopes");
};

const ensureTabExists = () => {
  if (!isClientDetailsRoute()) {
    return false;
  }

  const tabsHost = document.querySelector('[data-kc-component="client-tabs"] [role="tablist"]')
    || document.querySelector('[data-kc-component="client-tabs"]');

  if (!tabsHost) {
    return false;
  }

  if (document.querySelector('[data-oidc4vc-tab="true"]')) {
    return true;
  }

  const tabButton = document.createElement("button");
  tabButton.setAttribute("role", "tab");
  tabButton.setAttribute("type", "button");
  tabButton.className = "pf-c-tabs__link";
  tabButton.dataset.oidc4vcTab = "true";
  tabButton.textContent = "OIDC4VC";

  const tabWrapper = document.createElement("div");
  tabWrapper.className = "pf-c-tabs__item";
  tabWrapper.appendChild(tabButton);
  tabsHost.appendChild(tabWrapper);

  const panelHostId = "oidc4vc-client-tab-panel";
  let panelHost = document.getElementById(panelHostId);
  if (!panelHost) {
    panelHost = document.createElement("section");
    panelHost.id = panelHostId;
    panelHost.setAttribute("role", "tabpanel");
    panelHost.className = "pf-c-tab-content kc-oidc4vc-tab pf-u-mt-lg";
    panelHost.setAttribute("hidden", "");
    const anchor = document.querySelector('[data-kc-component="client-tabs-content"]')
      || document.querySelector('[role="tabpanel"]')?.parentElement;
    if (anchor) {
      anchor.appendChild(panelHost);
    } else {
      document.body.appendChild(panelHost);
    }

    MiniReact.render(
      MiniReact.createElement(
        "div",
        { className: "pf-c-card pf-m-shadow-sm pf-u-p-lg pf-u-w-75" },
        MiniReact.createElement("h1", { className: "pf-c-title pf-m-xl pf-u-mb-md" }, "OIDC4VC"),
        MiniReact.createElement(
          "p",
          { className: "pf-u-mb-md" },
          "Hello world – this tab is rendered from the OIDC4VC plugin React bundle."
        ),
        MiniReact.createElement(
          "small",
          { className: "pf-u-color-200" },
          "You can replace this component with your own React tree."
        )
      ),
      panelHost
    );
  }

  const switchToTab = () => {
    const allTabs = tabsHost.querySelectorAll('[role="tab"]');
    allTabs.forEach((tab) => {
      tab.classList.toggle("pf-m-current", tab === tabButton);
      tab.setAttribute("aria-selected", tab === tabButton ? "true" : "false");
    });

    const allPanels = panelHost.parentElement?.querySelectorAll('[role="tabpanel"]') || [];
    allPanels.forEach((panel) => {
      if (panel === panelHost) {
        panel.removeAttribute("hidden");
      } else {
        panel.setAttribute("hidden", "");
      }
    });
  };

  tabButton.addEventListener("click", (event) => {
    event.preventDefault();
    switchToTab();
  });

  return true;
};

const observer = new MutationObserver(() => {
  ensureTabExists();
});

window.addEventListener("hashchange", () => ensureTabExists());

observer.observe(document.body, { childList: true, subtree: true });

ensureTabExists();
