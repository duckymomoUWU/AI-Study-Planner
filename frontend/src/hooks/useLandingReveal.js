import { useEffect } from 'react'

export default function useLandingReveal(rootRef) {
  useEffect(() => {
    const root = rootRef.current
    if (!root || !('IntersectionObserver' in window)) return
    const motion = window.matchMedia('(prefers-reduced-motion: reduce)')
    if (motion.matches) return
    const elements = [...root.querySelectorAll('.landing-path, .landing-section-heading, .landing-step-grid>article, .landing-feature-copy, .landing-feature-row>.landing-product-preview, .landing-faq details, .landing-final-inner')]
    const show = element => { element.dataset.reveal = 'visible' }
    const observer = new IntersectionObserver(entries => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue
        show(entry.target)
        observer.unobserve(entry.target)
      }
    }, { threshold: 0.08, rootMargin: '0px 0px -24px 0px' })
    for (const element of elements) {
      element.classList.add('landing-reveal')
      const group = element.parentElement
      const index = group.matches('.landing-step-grid, .landing-faq-list') ? [...group.children].indexOf(element) : 0
      element.style.setProperty('--reveal-delay', `${Math.min(index, 2) * 100}ms`)
      if (element.getBoundingClientRect().top < window.innerHeight - 24) show(element)
      else { element.dataset.reveal = 'pending'; observer.observe(element) }
    }
    const revealFocused = event => {
      const element = event.target.closest('.landing-reveal')
      if (element) { show(element); observer.unobserve(element) }
    }
    const reduce = () => {
      if (motion.matches) { elements.forEach(show); observer.disconnect() }
    }
    root.addEventListener('focusin', revealFocused)
    motion.addEventListener('change', reduce)
    return () => {
      observer.disconnect()
      root.removeEventListener('focusin', revealFocused)
      motion.removeEventListener('change', reduce)
      for (const element of elements) {
        element.classList.remove('landing-reveal')
        delete element.dataset.reveal
        element.style.removeProperty('--reveal-delay')
      }
    }
  }, [rootRef])
}
