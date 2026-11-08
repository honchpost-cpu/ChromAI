## Preprocessing

All operations are applied to a duplicate of the image; original files
are never modified.

1. **Import** – images are opened via Bio-Formats Importer
   (Fiji version: X.X.X).
2. **Duplicate** – `Image > Duplicate` to preserve the raw file.
3. **Unification** – conversion to 8-bit (`Image > Type > 8-bit`) so that
   all images share the same data type.
4. **Noise reduction** – median filter, radius = [X] px
   (`Process > Filters > Median`).
5. **Contrast stabilization** – `Enhance Contrast`, saturated = [X]%,
   normalize = [yes/no] (applied per image).
6. **Pixel classification** – Trainable Weka Segmentation, two classes
   (`chromosome`, `background`); output is a 32-bit probability map
   for the `chromosome` class.
7. **Binarization** – threshold = [method/value] applied to the
   probability map.
8. **Mask cleaning (optional)** – fill holes, open/close ([X] iterations),
   watershed for touching objects.
9. **ROI extraction** – `Analyze Particles` (size = [min–max] px²,
   circularity = [min–max], exclude edges = [yes/no]); ROIs are saved
   to ROI Manager.
10. **Measurements** – Area, Perimeter, Feret, MinFeret, fit ellipse,
    shape descriptors (`Set Measurements`); exported as CSV together
    with `.zip` ROI sets.

Parameters are identical for all images unless stated otherwise.
