# ChromAI

Semi-automated chromosome segmentation and morphometry pipeline for non-model invertebrates, built with Fiji/ImageJ, Trainable Weka Segmentation (TWS) and R/Python.

ChromAI was developed for cytogenetic analysis of freshwater amphipods (*Gammarus fossarum*, *Chaetogammarus ischnus*). It replaces slow, subjective manual measurement of chromosomes on micrographs with a reproducible pipeline: pixel classification, ROI extraction, per-plate length normalization and rank-based profiling.

## Overview

Manual chromosome measurement is time-consuming and depends on the person doing it. ChromAI trains a pixel classifier on one species (*G. fossarum*), applies it to another (*C. ischnus*), and exports geometric measurements of every chromosomal object for statistical analysis. The approach was developed as part of a study presented at MOSTRATEC-LIBERATO 2026 (Project ID 562).

## Key results

- Five classifier configurations (v1–v5) were trained on *G. fossarum* micrographs and compared with a 4-criterion rubric (maximum score 8). **v4 performed best** on both species (6.2 on *G. fossarum*, 5.0 on *C. ischnus*).
- v4 transferred to *C. ischnus* images without retraining; other versions were more sensitive to species differences.
- Meiotic bivalents of *G. fossarum* were more elongated (mean aspect ratio ≈ 1.91) than those of *C. ischnus* (≈ 1.55).
- In *G. fossarum*, the 10 longest elements held ≈ 40.5% of total complement length (C10).
- Rank profiles were reproducible between plates (pairwise Pearson r = 0.91–0.96 for *C. ischnus*).

These results come from a small number of plates (see [Limitations](#limitations)).

## Pipeline

```
micrograph → preprocessing → TWS pixel classification → probability map
→ threshold → binary mask → cleanup → Analyze Particles → ROIs + CSV
→ normalization (R/Python) → rank profiles, idiograms, C10, AR, CI
```

## Requirements

- Fiji/ImageJ: version [- Fiji (ImageJ 1.54p), Java [1.8.0_452], Trainable Weka Segmentation 4.0.0 (plugin v. shown in Plugins > Segmentation)] (Bio-Formats Importer, Trainable Weka Segmentation)
- R: [2026.08.0] with `tidyverse`
- Python: [3.6.9] (see `environment/requirements.txt`, if present)

## Quick start

1. Open an image in Fiji (via Bio-Formats Importer) and duplicate it.
2. Apply the preprocessing steps below.
3. Open `Plugins > Segmentation > Trainable Weka Segmentation`, click *Load classifier* and select `fiji/models/tws_v4.model`.
4. Click *Get probability*, threshold the `chromosomes` probability map, clean the mask and run `Analyze Particles`.
5. Export measurements to CSV and ROIs as `RoiSet.zip`.

Example ROI set: `data/measurements/` (ROIs for *C. ischnus*, metaphase 22).

## Preprocessing

All operations are applied to a duplicate; original files are never modified. Full details: [`docs/preprocessing.md`](docs/preprocessing.md).

| Step | Operation | Parameters |
|---|---|---|
| 1 | Import | Bio-Formats Importer |
| 2 | Duplicate | `Image > Duplicate` |
| 3 | Unify data type | 8-bit |
| 4 | Noise reduction | Median filter, radius [not recorded] px |
| 5 | Contrast stabilization | [not recorded] |

> Parameters marked *not recorded* were not saved in the original scripts. They are being restored from the final working procedure and will be updated.

## Segmentation models

### Final model (v4)

Model file: `fiji/models/tws_v4.model`

| Setting | Value |
|---|---|
| Plugin | Trainable Weka Segmentation, 2D, grayscale |
| Classes | `chromosomes`, `background` |
| Features | Gaussian blur, Sobel, Hessian (and derived), Difference of Gaussians, Membrane projections |
| Sigma | 1–16 |
| Membrane projections | patch size 19, thickness 1 |
| Number of features | 76 |
| Classifier | FastRandomForest |
| Trees | 200 |
| Features per node | 2 |
| Max depth | unlimited (0) |
| Random seed | 439589309 |

These are TWS default settings. Training set: [N] images; test set: [N] images not used for training.

### Evaluation rubric

For each image–model pair, the mask was checked visually against the original and scored 0–2 on four criteria (0 = unsatisfactory, 1 = acceptable, 2 = good; **higher is always better**). The overall score is the sum (maximum 8).

| Criterion | What it checks |
|---|---|
| Coverage | completeness of chromosome selection |
| Waste | false-positive inclusions (higher = fewer) |
| Clumping/tearing | correct separation without fusion or fragmentation |
| Outline | contour quality for measurement |

### Comparison of v1–v5

*Trained and tested on* G. fossarum:

| Criterion | v1 | v2 | v3 | v4 | v5 |
|---|---|---|---|---|---|
| Coverage | 1.86 | 1.80 | 2.00 | 2.00 | 1.40 |
| Waste | 0.90 | 1.13 | 0.90 | 1.40 | 1.30 |
| Clumping/tearing | 1.06 | 1.20 | 0.80 | 1.06 | 1.30 |
| Outline | 1.46 | 1.30 | 1.30 | 1.70 | 1.10 |
| **Overall** | 5.28 | 5.40 | 5.00 | **6.20** | 5.10 |

*Transferred to* C. ischnus:

| Criterion | v1 | v2 | v3 | v4 | v5 |
|---|---|---|---|---|---|
| Coverage | 1.4 | 1.4 | 1.6 | 1.6 | 0.4 |
| Waste | 1.2 | 1.2 | 0.6 | 1.0 | 1.2 |
| Clumping/tearing | 0.6 | 0.8 | 0.8 | 1.0 | 0.4 |
| Outline | 0.8 | 0.8 | 1.0 | 1.4 | 0.4 |
| **Overall** | 4.0 | 4.2 | 4.0 | **5.0** | 2.4 |

Only v4 is released. Settings of v1–v3 and v5 were not recorded; they are reported as experimental variants.

## ROI extraction and measurements

Probability map → threshold [method/value] → binary mask → morphological cleanup (e.g. Close, [iterations]) → `Analyze Particles` (size [min–max] px², circularity [min–max], exclude on edges [yes/no]) → ROI Manager.

Measurements (`Set Measurements`): Area, Perimeter, Feret, MinFeret, fit ellipse, shape descriptors. Exported as CSV together with ROI sets.

Raw ROI counts are filtered before downstream analysis (e.g. metaphase 22: 37 raw ROIs, 34 after filtering). Lengths are in pixels unless a scale is stated.

## Output and metrics

| Metric | Definition |
|---|---|
| `RelLen_norm` / `RelFeretSum_adj` | length of an object divided by the sum of lengths of all objects in the same image (sums to 1 per image) |
| `SeqRank` | rank after sorting objects by relative length, descending (1 = largest) |
| C10 | sum of relative lengths of the 10 largest objects |
| AR | aspect ratio | Feret | MinFeret | eclipse | major/minor |
| CI | centromeric index: p/p+q *100% |
| Std RMSE | RMSE between length profiles resampled to normalized position (0 = longest, 1 = shortest) |

## Reproducing the figures

Scripts for rank profiles, idiograms, C10, cumulative curves and CI scatter plots will be added to `analysis/R/` and `analysis/python/`.

## Limitations

- *C. ischnus* karyotype estimates rest on **two** complete meiotic metaphase plates (four analysed, two used for chromosome counts).
- Cross-species transfer was good for v4 only; v1–v3 and v5 were more sensitive to species differences.
- Some preprocessing and thresholding parameters were not recorded and are being restored.
- Segmentation quality scores are semi-quantitative and visual.
- Object counts depend on plate quality (overlap, fragmentation, removal of small objects).
- Possible B-chromosomes are inferred from size and morphometric outliers, not confirmed by independent methods.

## Data availability

Example ROIs and measurements are in `data/measurements/`. Raw micrographs and *G. fossarum* data from the INRAE collaboration are shared only with the partners' agreement.

## Citation

If you use ChromAI, please cite this repository (see `CITATION.cff` once available).

## License

[MIT or other — to be added in `LICENSE`]

## Contact

Sofiia Honcharova — honchpost@gmail.com
