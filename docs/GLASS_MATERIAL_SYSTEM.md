# Aurora Glass material system

`VimivoGlassSurface` is the shared Compose primitive. Its variants are navigation, floating player, control, sheet, popover, and solid fallback.

It uses stable translucent Material surfaces, a one-pixel edge, tonal elevation, and a soft shadow. It deliberately avoids live backdrop blur so scrolling and native video rendering keep a predictable GPU budget.

## Usage

- Use glass for navigation, player docks, queue sheets, compact controls, and popovers.
- Keep media rows, grids, settings groups, and video frames solid.
- Do not stack translucent surfaces where one parent material is sufficient.
- Do not blur a playing video or decode artwork on the UI thread for decoration.

## Accessibility fallback

`LocalReducedTransparency` forces opaque `surfaceContainerHigh`. High-contrast mode enables the same fallback; borders become stronger and decorative shadows are removed.

Platform settings must still project native or persisted reduced-transparency state into this local. The primitive is implemented; automatic platform wiring is not yet complete.
