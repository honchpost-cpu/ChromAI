# Preprocessing

All operations are applied to a duplicate of the image. Original files are never modified.

| Step | Operation | Fiji command | Parameters |
|---|---|---|---|
| 1 | Import | Bio-Formats Importer | default |
| 2 | Duplicate | `Image > Duplicate` | – |
| 3 | Unify data type | `Image > Type > 8-bit` | – |
| 4 | Noise reduction | `Process > Filters > Median` | radius: [not recorded] px |
| 5 | Contrast stabilization | `Process > Enhance Contrast` | saturated: [not recorded]%, normalize: [not recorded] |
| 6 | Pixel classification | Trainable Weka Segmentation | model `fiji/models/tws_v4.model` |
| 7 | Binarization | `Image > Adjust > Threshold` | [method/value, not recorded] |
| 8 | Mask cleanup | `Process > Binary` | Close, [iterations]; fill holes: [yes/no] |
| 9 | ROI extraction | `Analyze > Analyze Particles` | size [min–max] px², circularity [min–max], exclude on edges [yes/no] |
| 10 | Measurements | `Analyze > Set Measurements` | Area, Perimeter, Feret, MinFeret, fit ellipse, shape descriptors |

## Notes

- Parameters marked *not recorded* were not saved in the original scripts and are being restored from the final working procedure.
- Software: Fiji (ImageJ 1.54x), Java [version], Trainable Weka Segmentation (2D, grayscale).
- Raw ROI counts are filtered before downstream analysis (e.g. metaphase 22: 37 raw ROIs, 34 after filtering). The filtering criteria are: [describe].
- Lengths and areas are in pixels unless a scale (µm/px) is stated.
