/**
 * ClearPath — UI extras
 * - Entrance animations (fade+rise on sections entering viewport)
 * - Screen transition animation
 * - Accessible button feedback
 * - prefers-reduced-motion guard
 */
(function () {
  const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* ── Entrance animations ────────────────────────────────── */
  if (!reduced && 'IntersectionObserver' in window) {
    const targets = document.querySelectorAll(
      '.profile-section, .permission-card, .sim-controls-panel, .home-cta, .field-group'
    );

    const io = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        if (entry.isIntersecting) {
          entry.target.classList.add('entered');
          io.unobserve(entry.target);
        }
      });
    }, { threshold: 0.12 });

    targets.forEach((el, i) => {
      if (!reduced) {
        el.style.opacity = '0';
        el.style.transform = 'translateY(20px)';
        el.style.transition = `opacity 380ms ease ${i * 60}ms, transform 380ms ease ${i * 60}ms`;
      }
      io.observe(el);
    });

    document.addEventListener('animationend', () => {}, { once: true });

    // Trigger for elements already in view
    setTimeout(() => {
      targets.forEach(el => {
        const rect = el.getBoundingClientRect();
        if (rect.top < window.innerHeight) {
          el.classList.add('entered');
        }
      });
    }, 80);
  }

  /* ── .entered class triggers the transition ─────────────── */
  const style = document.createElement('style');
  style.textContent = `
    .entered {
      opacity: 1 !important;
      transform: translateY(0) !important;
    }
    @media (prefers-reduced-motion: reduce) {
      .profile-section, .permission-card, .sim-controls-panel, .home-cta, .field-group {
        opacity: 1 !important;
        transform: none !important;
        transition: none !important;
      }
    }
  `;
  document.head.appendChild(style);

  /* ── Button press feedback ───────────────────────────────── */
  if (!reduced) {
    document.querySelectorAll('.btn-start-duty, .btn-primary-lg, .btn-ack').forEach(btn => {
      btn.addEventListener('pointerdown', () => {
        btn.style.transform = 'scale(0.97)';
      });
      btn.addEventListener('pointerup', () => {
        btn.style.transform = '';
      });
      btn.addEventListener('pointerleave', () => {
        btn.style.transform = '';
      });
    });
  }

  /* ── Start-duty button label animation ──────────────────── */
  const startBtn = document.getElementById('startDutyBtn');
  if (startBtn) {
    startBtn.addEventListener('click', () => {
      const original = startBtn.textContent;
      startBtn.textContent = 'Starting...';
      startBtn.disabled = true;
      setTimeout(() => {
        // Re-enable only if still on home screen
        if (document.getElementById('homeScreen').style.display !== 'none') {
          startBtn.textContent = original;
          startBtn.disabled = false;
        }
      }, 4000);
    });
  }
})();
