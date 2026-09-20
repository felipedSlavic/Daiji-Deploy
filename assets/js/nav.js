(function () {
 
  // Acentos escritos como \uXXXX (ASCII puro) para não depender da
  // codificação com que o navegador lê este arquivo .js.
  var LINKS = [
    { href: "index.html",           label: "Início" },        // Início
    { href: "especialidades.html",  label: "Especialidades" },
    { href: "telemedicina.html",    label: "Telemedicina" },
    { href: "planos.html",          label: "Planos" },
    { href: "como-funciona.html",   label: "Como Funciona" },
    { href: "estrategia.html",      label: "Estratégia" },    // Estratégia
    { href: "seguranca.html",       label: "Segurança" },     // Segurança
    { href: "sobre.html",           label: "Sobre" },
    { href: "contato.html",         label: "Contato" }
  ];
 
  // página atual (nome do arquivo) para marcar o item ativo
  var atual = (location.pathname.split("/").pop() || "index.html").toLowerCase();
  if (!atual) atual = "index.html";
 
  var itens = LINKS.map(function (l) {
    var ativo = (l.href.toLowerCase() === atual) ? ' class="active"' : "";
    return '<a href="' + l.href + '"' + ativo + '>' + l.label + "</a>";
  }).join("");
 
  var html =
    '<nav class="oura-nav" id="ouraNav">' +
      '<div class="row-nav">' +
        '<a class="brand" href="index.html">DA<span class="bar">|</span>JI</a>' +
        '<div class="links">' + itens + "</div>" +
        '<div class="right">' +
          '<button class="icon-btn" data-theme-toggle aria-label="Alternar tema">' +
            '<svg class="icon-sun" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4 12H2M22 12h-2M5 5l1.5 1.5M18.5 18.5 20 20M18.5 5.5 20 4M4 20l1.5-1.5"/></svg>' +
            '<svg class="icon-moon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8"><path d="M21 12.5A9 9 0 1111.5 3 7 7 0 0021 12.5z"/></svg>' +
          "</button>" +
          '<a class="btn-explore" style="padding:9px 20px; font-size:13px;" href="planos.html">Ver planos</a>' +
          '<button class="nav-burger" id="navBurger" aria-label="Abrir menu" aria-expanded="false" aria-controls="ouraNav">' +
            '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M4 7h16M4 12h16M4 17h16"/></svg>' +
          "</button>" +
        "</div>" +
      "</div>" +
    "</nav>";
 
  var alvo = document.getElementById("daijiNav");
  if (alvo) {
    alvo.outerHTML = html;
  } else {
    // fallback: injeta no topo do body se o placeholder não existir
    document.body.insertAdjacentHTML("afterbegin", html);
  }
 
  /* ---------- menu hambúrguer (telas menores) ---------- */
  var nav = document.getElementById("ouraNav");
  var burger = document.getElementById("navBurger");
 
  if (nav && burger) {
 
    burger.addEventListener("click", function () {
      var aberto = nav.classList.toggle("open");
      burger.setAttribute("aria-expanded", aberto ? "true" : "false");
      burger.setAttribute("aria-label", aberto ? "Fechar menu" : "Abrir menu");
    });
 
    // fecha o menu ao clicar em um link
    nav.querySelectorAll(".links a").forEach(function (link) {
      link.addEventListener("click", function () {
        nav.classList.remove("open");
        burger.setAttribute("aria-expanded", "false");
        burger.setAttribute("aria-label", "Abrir menu");
      });
    });
 
    // fecha ao clicar fora do menu
    document.addEventListener("click", function (ev) {
      if (nav.classList.contains("open") && !nav.contains(ev.target)) {
        nav.classList.remove("open");
        burger.setAttribute("aria-expanded", "false");
        burger.setAttribute("aria-label", "Abrir menu");
      }
    });
  }
 
})();