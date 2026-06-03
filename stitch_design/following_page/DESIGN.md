---
name: Mihon Design System
colors:
  surface: '#f9f9fb'
  surface-dim: '#d9dadc'
  surface-bright: '#f9f9fb'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f3f5'
  surface-container: '#edeef0'
  surface-container-high: '#e8e8ea'
  surface-container-highest: '#e2e2e4'
  on-surface: '#1a1c1d'
  on-surface-variant: '#44474E'
  inverse-surface: '#2f3132'
  inverse-on-surface: '#f0f0f2'
  outline: '#767586'
  outline-variant: '#c7c4d7'
  surface-tint: '#4849da'
  primary: '#4343d5'
  on-primary: '#ffffff'
  primary-container: '#5d5fef'
  on-primary-container: '#faf7ff'
  inverse-primary: '#c1c1ff'
  secondary: '#5b5c76'
  on-secondary: '#ffffff'
  secondary-container: '#e0e0ff'
  on-secondary-container: '#61627c'
  tertiary: '#7c5000'
  on-tertiary: '#ffffff'
  tertiary-container: '#9d6600'
  on-tertiary-container: '#fff6ef'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e1e0ff'
  primary-fixed-dim: '#c1c1ff'
  on-primary-fixed: '#07006c'
  on-primary-fixed-variant: '#2e2bc2'
  secondary-fixed: '#e0e0ff'
  secondary-fixed-dim: '#c4c4e2'
  on-secondary-fixed: '#181a30'
  on-secondary-fixed-variant: '#43455e'
  tertiary-fixed: '#ffddb4'
  tertiary-fixed-dim: '#ffb954'
  on-tertiary-fixed: '#291800'
  on-tertiary-fixed-variant: '#633f00'
  background: '#f9f9fb'
  on-background: '#1a1c1d'
  surface-variant: '#e2e2e4'
  surface-dark: '#121212'
  surface-container-dark: '#1E1E1E'
  success-green: '#2D6A4F'
typography:
  headline-lg:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-lg-mobile:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: '700'
    lineHeight: 36px
  title-md:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label-lg:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0.1px
  label-sm:
    fontFamily: Inter
    fontSize: 11px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.5px
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  base: 4px
  gutter-mobile: 16px
  gutter-desktop: 24px
  margin-screen: 16px
  stack-sm: 8px
  stack-md: 16px
  stack-lg: 32px
---

## Brand & Style
The design system is rooted in the principles of **Material Design 3 (M3)**, optimized for a high-utility content consumption experience. It balances technical proficiency with a clean, welcoming aesthetic that recedes to prioritize manga artwork. 

The brand personality is **functional, open, and community-driven**. It avoids unnecessary ornamentation, favoring a modular "container-based" architecture. The visual style is **Corporate Modern with a content-centric focus**, utilizing systematic spacing and tonal layering to organize large libraries of information without overwhelming the user.

## Colors
The palette is anchored by a **Vibrant Indigo** primary color, used for key actions and brand identification. 

- **Light Mode:** Utilizes a clean `#F9F9FB` background to provide a paper-like reading environment. Surfaces are defined by subtle shifts in lightness rather than heavy borders.
- **Dark Mode:** Employs a deep charcoal (`#121212`) to reduce eye strain during night reading, with containers using `#1E1E1E` to establish hierarchy.
- **Functional Accents:** Tertiary gold is used sparingly for "New" status indicators or specific tracking updates, ensuring they stand out against the primary indigo.

## Typography
This design system utilizes **Inter** for all roles to achieve a modern, neutral, and highly legible interface. The type scale follows M3 conventions, emphasizing clear hierarchy between series titles and metadata.

Headlines use a tighter letter-spacing and heavier weights to command attention in hero sections. Labels and small metadata (like chapter numbers or timestamps) utilize a slightly increased letter-spacing to maintain readability at small scales on mobile displays.

## Layout & Spacing
The layout follows a **4dp grid system**, ensuring all elements align to a consistent rhythmic scale. 

- **Grid System:** For the Library and Browse views, a fluid responsive grid is used. 
  - **Mobile:** 2-column layout for manga covers.
  - **Tablet:** 4-6 column layout.
  - **Desktop:** 8-12 column layout.
- **Safe Areas:** A 16px margin is maintained at the screen edges for mobile devices.
- **Vertical Rhythm:** A "Stack" philosophy is applied where related items (title and author) use `stack-sm`, while distinct sections use `stack-lg`.

## Elevation & Depth
In accordance with Material Design 3, this system prioritizes **Tonal Layers** over heavy shadows. 

- **Level 0 (Floor):** The primary background.
- **Level 1 (Cards):** Utilizes a subtle surface tint (Indigo at 5% opacity) or a 1px low-contrast outline in light mode.
- **Level 2 (Navigation/Menus):** Elevated using soft, ambient shadows (0px 4px 12px, 8% black) to indicate interactive priority.
- **Bottom Navigation:** Fixed at the bottom with a subtle background blur (glassmorphism) in dark mode to provide context of the content scrolling beneath it.

## Shapes
The shape language is consistently **Rounded**, mirroring the friendly but organized nature of the app.

- **Manga Covers:** Use `rounded-lg` (16px) to soften the density of the grid.
- **Buttons & Chips:** Follow the full pill-shape (`rounded-xl` / 24px+) for high affordance.
- **Input Fields:** Utilize `rounded-lg` to match the card containers, creating a unified structural language across the settings and search interfaces.

## Components

- **Buttons:** 
  - **Primary:** Solid indigo fill with white text; fully pill-shaped.
  - **Secondary:** Tonal indigo fill (light indigo) with dark indigo text.
- **Manga Cards:** Vertical aspect ratio containers. The image should have a 16px radius. Title text is placed directly below or as an overlay with a bottom-to-top gradient scrim for legibility.
- **Chips:** Used for genre tags and categories. Low-stroke or tonal fill, always pill-shaped with `label-sm` typography.
- **Bottom Navigation:** Utilizes outlined Material Symbols. Active states are indicated by a pill-shaped tonal highlight behind the icon.
- **Input Fields:** Outlined style with a 1px border. On focus, the border thickness increases to 2px and adopts the primary indigo color.
- **Progress Indicators:** Linear bars for "Read" status on manga covers, positioned at the very bottom edge of the image container.