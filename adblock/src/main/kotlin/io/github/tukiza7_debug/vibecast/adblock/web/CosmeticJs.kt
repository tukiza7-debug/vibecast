package io.github.tukiza7_debug.vibecast.adblock.web

/**
 * JavaScript snippets for cosmetic filtering. applyCss re-appends the stylesheet if
 * the page removed it, so hiding survives DOM changes without a native callback.
 */
object CosmeticJs {

    fun applyCss(escapedCss: String): String = """
        (function() {
          var css = `$escapedCss`;
          var style = document.getElementById('vc-adblock-style');
          if (!style) {
            style = document.createElement('style');
            style.id = 'vc-adblock-style';
            (document.head || document.documentElement).appendChild(style);
          }
          if (style.textContent !== css) {
            style.textContent = css;
          }
        })();
    """.trimIndent()

    fun removeHidden(selectorRegex: String): String = """
        (function() {
          var pattern = new RegExp('^(' + "$selectorRegex" + ')$');
          var style = document.getElementById('vc-adblock-style');
          if (!style) return;
          var parts = style.textContent.replace('{display:none!important;visibility:hidden!important}', '').split(',');
          var kept = parts.filter(function(s) { return !pattern.test(s); });
          if (kept.length === 0) { style.remove(); } else { style.textContent = kept.join(',') + '{display:none!important;visibility:hidden!important}'; }
        })();
    """.trimIndent()

    fun mutationObserver(): String = """
        (function() {
          if (window.__vcCosmeticObserver) return;
          window.__vcCosmeticObserver = new MutationObserver(function() {
            var style = document.getElementById('vc-adblock-style');
            if (!style) {
              var event = new Event('vccosmeticrestore');
              document.dispatchEvent(event);
            }
          });
          window.__vcCosmeticObserver.observe(document.documentElement, { childList: true, subtree: true });
        })();
    """.trimIndent()
}
