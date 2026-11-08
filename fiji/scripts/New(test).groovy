// ============================================================
//  TWS_Segment_and_Measure.groovy
//  Fiji script: preprocessed image -> Weka segmentation (TWS)
//  -> mask (.tif) + measurement table (.csv) + ROIs (.zip)
//  Open in Fiji Script Editor, Language > Groovy, press Run.
// ============================================================
import ij.*
import ij.gui.*
import ij.io.*
import ij.measure.*
import ij.process.*
import ij.plugin.frame.RoiManager
import ij.plugin.filter.ParticleAnalyzer
import trainableSegmentation.WekaSegmentation

// ---------- 1) PROJECT FOLDER (edit default if needed) ----------
def sep = File.separator
def defaultRoot = System.getProperty("user.home") + sep + "Documents" + sep + "MVP(werk)" + sep + "CI"

def gd0 = new GenericDialog("Project folder")
gd0.addStringField("Root folder (contains preprocessed, classifiers, masks...):", defaultRoot, 50)
gd0.showDialog()
if (gd0.wasCanceled()) return
def root = gd0.getNextString()

def dirPre   = new File(root, "preprocessed")
def dirCls   = new File(root, "classifiers")
def dirMasks = new File(root, "masks")
def dirTabs  = new File(root, "tables")
def dirRois  = new File(root, "rois")

if (!dirPre.isDirectory() || !dirCls.isDirectory()) {
    IJ.error("Folders not found", "Could not find 'preprocessed' and/or 'classifiers' in:\n" + root)
    return
}
[dirMasks, dirTabs, dirRois].each { it.mkdirs() }

def classifierFiles = dirCls.listFiles({ d, n -> n.toLowerCase().endsWith(".model") } as FilenameFilter)
if (classifierFiles == null || classifierFiles.length == 0) {
    IJ.error("No classifier", "No .model files found in:\n" + dirCls.getAbsolutePath())
    return
}
def classifierNames = classifierFiles.collect { it.getName() } as String[]
Arrays.sort(classifierNames)

// ---------- 2) SETTINGS UI ----------
def gd = new GenericDialog("TWS segmentation & measurement")
gd.addMessage("INPUT")
gd.addChoice("Files to process:", ["All images in 'preprocessed'", "Pick a single file"] as String[], "All images in 'preprocessed'")
gd.addChoice("Classifier (.model):", classifierNames, classifierNames[0])
gd.addNumericField("Class to extract (1 = first class):", 1, 0)

gd.addMessage("PRE-PROCESSING (cosmetic - keep consistent with classifier training!)")
gd.addCheckbox("Gaussian blur", false);          gd.addNumericField("   sigma (px):", 1.0, 1)
gd.addCheckbox("Subtract background", false);    gd.addNumericField("   rolling ball radius:", 50, 0)
gd.addCheckbox("Median filter", false);          gd.addNumericField("   radius:", 1.0, 1)
gd.addCheckbox("Enhance contrast (normalize)", false)

gd.addMessage("MASK POST-PROCESSING")
gd.addCheckbox("Fill holes", true)
gd.addCheckbox("Open (remove specks)", false)
gd.addCheckbox("Watershed (split touching objects)", true)

gd.addMessage("PARTICLE ANALYSIS")
gd.addNumericField("Min size (px^2):", 50, 0)
gd.addNumericField("Max size (px^2, 0 = no limit):", 0, 0)
gd.addNumericField("Min circularity (0-1):", 0.0, 2)
gd.addCheckbox("Exclude objects on edges", false)

gd.addMessage("OUTPUT")
gd.addCheckbox("Save overlay preview (PNG)", true)
gd.addCheckbox("Close images when finished", true)
gd.showDialog()
if (gd.wasCanceled()) return

def allFiles     = gd.getNextChoiceIndex() == 0
def clsName      = gd.getNextChoice()
def targetClass  = (int) gd.getNextNumber()
def doGauss      = gd.getNextBoolean();  def sigma   = gd.getNextNumber()
def doBg         = gd.getNextBoolean();  def ballR   = gd.getNextNumber()
def doMedian     = gd.getNextBoolean();  def medR    = gd.getNextNumber()
def doNormalize  = gd.getNextBoolean()
def doFill       = gd.getNextBoolean()
def doOpen       = gd.getNextBoolean()
def doWatershed  = gd.getNextBoolean()
def minSize      = gd.getNextNumber()
def maxSize      = gd.getNextNumber()
def minCirc      = gd.getNextNumber()
def exclEdges    = gd.getNextBoolean()
def saveOverlay  = gd.getNextBoolean()
def closeAtEnd   = gd.getNextBoolean()

if (maxSize <= 0) maxSize = Double.POSITIVE_INFINITY
def classifierPath = new File(dirCls, clsName).getAbsolutePath()

// ---------- 3) BUILD FILE LIST ----------
def exts = [".tif", ".tiff", ".png", ".jpg", ".jpeg"]
def files = []
if (allFiles) {
    dirPre.listFiles().sort { it.name }.each { f ->
        if (f.isFile() && exts.any { f.name.toLowerCase().endsWith(it) }) files << f
    }
} else {
    def od = new OpenDialog("Pick an image from 'preprocessed'", dirPre.getAbsolutePath(), null)
    if (od.getFileName() == null) return
    files << new File(od.getDirectory(), od.getFileName())
}
if (files.isEmpty()) { IJ.error("No images found in " + dirPre); return }

def stamp = new Date().format("yyyy-MM-dd HH:mm")
new File(dirTabs, "run_settings.txt").append(
  "${stamp} | ImageJ=${IJ.getFullVersion()} | classifier=${clsName} | class=${targetClass}" +
  " | gauss=${doGauss}(${sigma}) | bg=${doBg}(${ballR}) | median=${doMedian}(${medR}) | normalize=${doNormalize}" +
  " | fill=${doFill} | open=${doOpen} | watershed=${doWatershed}" +
  " | minSize=${minSize} | maxSize=${maxSize} | minCirc=${minCirc} | exclEdges=${exclEdges}" +
  " | files=${files.size()}\n")
  
// ---------- 4) PROCESSING ----------
def oldBlackBg = Prefs.blackBackground
Prefs.blackBackground = true
def rm = new RoiManager(false)
ParticleAnalyzer.setRoiManager(rm)

def processFile = { File f ->
    def base = f.getName().replaceFirst(/\.[^.]+$/, "")
    IJ.log("Processing: " + f.getName())

    def imp = IJ.openImage(f.getAbsolutePath())
    if (imp == null) { IJ.log("  ! could not open"); return }

    // --- working copy for segmentation (cosmetic filters) ---
    def work = imp.duplicate()
    work.setTitle(base + "_work")
    if (doBg)        IJ.run(work, "Subtract Background...", "rolling=" + ballR)
    if (doMedian)    IJ.run(work, "Median...", "radius=" + medR)
    if (doGauss)     IJ.run(work, "Gaussian Blur...", "sigma=" + sigma)
    if (doNormalize) IJ.run(work, "Enhance Contrast...", "saturated=0.35 normalize")

    // --- Trainable Weka Segmentation ---
    def ws = new WekaSegmentation(work)
    if (!ws.loadClassifier(classifierPath)) {
        IJ.log("  ! failed to load classifier"); ws.shutDownNow(); return
    }
    def result = ws.applyClassifier(work, 0, false)   // 0 = all threads, label image
    ws.shutDownNow()
    if (result == null) { IJ.log("  ! segmentation failed"); return }

    // --- classified image -> binary mask (255 = chosen class) ---
    def rip = result.getProcessor()
    int w = rip.getWidth(), h = rip.getHeight()
    def mip = new ByteProcessor(w, h)
    for (int i = 0; i < w * h; i++) {
        if (rip.get(i) == (targetClass - 1)) mip.set(i, 255)
    }
    def mask = new ImagePlus(base + "_mask", mip)
    mask.setCalibration(imp.getCalibration())

    if (doFill)      IJ.run(mask, "Fill Holes", "")
    if (doOpen)      IJ.run(mask, "Open", "")
    if (doWatershed) IJ.run(mask, "Watershed", "")

    // --- particle analysis -> table + ROIs ---
    rm.reset()
    def rt = new ResultsTable()
    mask.getProcessor().setThreshold(255, 255, ImageProcessor.NO_LUT_UPDATE)
    int opts = ParticleAnalyzer.ADD_TO_MANAGER | ParticleAnalyzer.SHOW_NONE
    if (exclEdges) opts |= ParticleAnalyzer.EXCLUDE_EDGE_PARTICLES
    int meas = Measurements.AREA | Measurements.PERIMETER | Measurements.CENTROID |
               Measurements.SHAPE_DESCRIPTORS | Measurements.FERET | Measurements.ELLIPSE
    def pa = new ParticleAnalyzer(opts, meas, rt, minSize, maxSize, minCirc, 1.0)
    pa.analyze(mask)

    // add intensity from the ORIGINAL image + image name to each row
    int n = rm.getCount()
    for (int i = 0; i < n; i++) {
        imp.setRoi(rm.getRoi(i))
        def st = imp.getStatistics(Measurements.MEAN | Measurements.MIN_MAX)
        rt.setValue("Image", i, f.getName())
        rt.setValue("Object", i, i + 1)
        rt.setValue("MeanIntensity", i, st.mean)
        rt.setValue("MinIntensity", i, st.min)
        rt.setValue("MaxIntensity", i, st.max)
    }
    imp.deleteRoi()

    // --- save everything ---
    mask.getProcessor().resetThreshold()
    IJ.saveAs(mask, "Tiff", new File(dirMasks, base + "_mask.tif").getAbsolutePath())
    rt.saveAs(new File(dirTabs, base + "_table.csv").getAbsolutePath())
    if (n > 0) {
        rm.runCommand("Save", new File(dirRois, base + "_rois.zip").getAbsolutePath())
    }

    if (saveOverlay && n > 0) {
        def prev = imp.duplicate()
        if (prev.getBitDepth() != 24) {
            IJ.run(prev, "Enhance Contrast", "saturated=0.35")
            IJ.run(prev, "8-bit", "")
            IJ.run(prev, "RGB Color", "")
        }
        def ov = new Overlay()
        for (int i = 0; i < n; i++) {
            def r = rm.getRoi(i).clone()
            r.setStrokeColor(java.awt.Color.YELLOW)
            ov.add(r)
        }
        prev.setOverlay(ov)
        def flat = prev.flatten()
        IJ.saveAs(flat, "PNG", new File(dirMasks, base + "_overlay.png").getAbsolutePath())
        flat.close(); prev.close()
    }

    IJ.log("  -> " + n + " objects | mask, table, ROIs saved")
    imp.close(); work.close(); result.close(); mask.close()
}

files.each { processFile(it) }

rm.reset()
rm.close()
Prefs.blackBackground = oldBlackBg
IJ.log("DONE. Processed " + files.size() + " file(s).")
IJ.showStatus("TWS batch finished")
