
// QCApp 2, 24 Sep 2012, ~/Applications/IdeaProjects/QCApp/src/QCApp

import java.awt.*;
import java.awt.event.*;
import java.awt.font.FontRenderContext;
import java.awt.image.*;
import java.awt.geom.*;

import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;

import java.io.*;
import java.nio.*;
import javax.imageio.*;

import java.nio.charset.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Vector;
import java.util.zip.GZIPInputStream;
import java.util.InputMismatchException;
import java.util.List;

class MyVolume {
	final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;

	final static short DT_UINT8 = 2;
	final static short DT_INT16 = 4;
	final static short DT_INT32 = 8;
	final static short DT_FLOAT32 = 16;

	int[][][] volume; // 3d volume data
	int[] dim = new int[3]; // 3d volume dimensions (1st:dim[1])
	float[] pixdim = new float[3]; // 3d volume pixel dimensions
	short datatype; // 3d volume data type
	int[][] boundingBox = new int[2][6]; // 3d volume bounding box
	float[][] S = new float[3][3]; // coordinate systems matrix

	String volName;

	int bytesPerVoxel() {
		int bpv = 0;
		switch (datatype) {
		case 2:
			bpv = 1;
			break;// DT_UINT8
		case 4:
			bpv = 2;
			break;// DT_INT16
		case 8:
			bpv = 4;
			break;// DT_INT32
		case 16:
			bpv = 4;
			break;// DT_FLOAT32
		}
		return bpv;
	}

	int loadFreeSurferVolume(String directory, String volName) {
		int err = 0;
		final int HEADER_SIZE = 284;
		final int MGHUCHAR = 0;
		final int MGHINT = 1;
		final int MGHFLOAT = 3;
		final int MGHSHORT = 4;
		byte[] buffer;

		try {
			// Read volume data
			String filename = directory + "/" + volName + ".mgz";
			FileInputStream fis = new FileInputStream(filename);
			GZIPInputStream gis = new GZIPInputStream(fis);
			DataInputStream dis = new DataInputStream(gis);
			byte b[] = new byte[HEADER_SIZE]; // total header size
			ByteBuffer bb = ByteBuffer.wrap(b);

			dis.readFully(b, 0, HEADER_SIZE);

			// bb.order(ByteOrder.nativeOrder());
			bb.order(BYTE_ORDER);
			// System.out.println("sizeof_hdr: "+bb.getInt());
			// dim[0] = 1; // <15 => littleEndian
			dim[0] = bb.getInt(4);
			dim[1] = bb.getInt(8);
			dim[2] = bb.getInt(12);
			// System.out.println("dim:
			// "+dim[0]+","+dim[1]+","+dim[2]);
			int mghtype = bb.getInt(20);
			switch (mghtype) {
			case MGHUCHAR:
				datatype = DT_UINT8;
				break;
			case MGHSHORT:
				datatype = DT_INT16;
				break;
			case MGHINT:
				datatype = DT_INT32;
				break;
			case MGHFLOAT:
				datatype = DT_FLOAT32;
				break;
			}
			// System.out.println("datatype: "+datatype);
			pixdim[0] = bb.getFloat(30);
			pixdim[1] = bb.getFloat(34);
			pixdim[2] = bb.getFloat(38);
			// System.out.println("pixdim:
			// "+pixdim[0]+","+pixdim[1]+","+pixdim[2]);
			
			S[0][0] = bb.getFloat(42);
			S[0][1] = bb.getFloat(46);
			S[0][2] = bb.getFloat(50);
			S[1][0] = bb.getFloat(54);
			S[1][1] = bb.getFloat(58);
			S[1][2] = bb.getFloat(62);
			S[2][0] = bb.getFloat(66);
			S[2][1] = bb.getFloat(70);
			S[2][2] = bb.getFloat(74);

			System.out.println("Allocating " + dim[0] * dim[1] * dim[2] * bytesPerVoxel() + " bytes");
			buffer = new byte[dim[0] * dim[1] * dim[2] * bytesPerVoxel()];
			try {
				dis.readFully(buffer, 0, buffer.length);
				dis.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
			
			bb = ByteBuffer.wrap(buffer);
			bb.order(BYTE_ORDER);
			volume = new int[dim[0]][dim[1]][dim[2]];
			for (int k = 0; k < dim[2]; k += 1)
				for (int j = 0; j < dim[1]; j += 1)
					for (int i = 0; i < dim[0]; i += 1) {
						switch (datatype) {
						case 2:// DT_UINT8
							volume[k][j][i] = bb.get() & 0xFF;
							break;
						case 4:// DT_INT16
							volume[k][j][i] = bb.getShort();
							break;
						case 8:// DT_INT32
							volume[k][j][i] = bb.getInt();
							break;
						}
					}
			
			for (int m=0; m<boundingBox.length; m++) {
				// Get bounding box
				boundingBox[m][0] = dim[0] - 1; // min i
				boundingBox[m][1] = 0; // max i
				boundingBox[m][2] = dim[1] - 1; // min j
				boundingBox[m][3] = 0; // max j
				boundingBox[m][4] = dim[2] - 1; // min k
				boundingBox[m][5] = 0; // max k
				float val;
				int rgb;
				int s = 5;
				for (int k = 0; k < dim[2]; k += s) // there's no need to scan all
													// voxels...
					for (int j = 0; j < dim[1]; j += s)
						for (int i = 0; i < dim[0]; i += s) {
							val = volume[k][j][i];
							rgb = MyImages.value2rgb((int)val, m);
							if (rgb > 0) {
								if (i < boundingBox[m][0])
									boundingBox[m][0] = i - s + 1;
								if (i > boundingBox[m][1])
									boundingBox[m][1] = i + s - 1;
								if (j < boundingBox[m][2])
									boundingBox[m][2] = j - s + 1;
								if (j > boundingBox[m][3])
									boundingBox[m][3] = j + s - 1;
								if (k < boundingBox[m][4])
									boundingBox[m][4] = k - s + 1;
								if (k > boundingBox[m][5])
									boundingBox[m][5] = k + s - 1;
							}
						}
				if (boundingBox[m][0] < 0)
					boundingBox[m][0] = 0;
				if (boundingBox[m][1] > dim[0] - 1)
					boundingBox[m][1] = dim[0] - 1;
				if (boundingBox[m][2] < 0)
					boundingBox[m][2] = 0;
				if (boundingBox[m][3] > dim[1] - 1)
					boundingBox[m][3] = dim[1] - 1;
				if (boundingBox[m][4] < 0)
					boundingBox[m][4] = 0;
				if (boundingBox[m][5] > dim[2] - 1)
					boundingBox[m][5] = dim[2] - 1;
			}

		} catch (IOException e) {
			err = 1;
		}

		return err;
	}

	public int getValue(int i, int j, int k)
	// get value at voxel with index coordinates i,j,k
	{
		return volume[k][j][i];
	}

	public MyVolume(String directory, String volName) {
		this.volName = volName;
		loadFreeSurferVolume(directory, volName);
	}
}

class MyVolumes {
	private List<MyVolume> volumes;
	String directory;

	public MyVolume getVolume(String volName) {
		for (MyVolume vol : volumes) {
			if (vol.volName.equals(volName))
				return vol;
		}
		QCApp.printStatusMessage("Loading volume \"" + volName + "\"...");
		MyVolume newVol = new MyVolume(directory, volName);
		volumes.add(newVol);
		return newVol;
	}

	public MyVolumes(String directory) {
		this.directory = directory;
		volumes = new ArrayList<MyVolume>();
	}
}

class MyImages extends JComponent {
	private static final long serialVersionUID = -1848304834958653184L;
	private Rectangle rect[];
	private Boolean toggle;
	private int selectedImage = 0;
	private int initialized = 0;
	private float[] selectedSlice = { 0.5f, 0.5f, 0.5f };
	private int yprev;
	private float prevSlice;
	private int prevPlane;
	private double prevHeight;
	private String subjectDir;
	private String imgList[];
	private BufferedImage img0; // single view bitmap image
	private BufferedImage img[]; // bitmap images
	static int cmap[][] = new int[2][256 * 3]; // colour maps
	
	private final static float[][] X = {	// X-plane transformation matrix
			{ 0, 1, 0 },
			{ 0, 0, -1 },
			{ 1, 0, 0 }
	};
	private final static float[][] Y = {	// Y-plane transformation matrix
			{ 1, 0, 0 },
			{ 0, 0, -1 },
			{ 0, 1, 0 }
	};
	private final static float[][] Z = {	// Z-plane transformation matrix
			{ 1, 0, 0 },
			{ 0, -1, 0 },
			{ 0, 0, 1 }
	};
	
	private MyVolumes volumes;

	public MyImages(String subjectDir) {
		this();
		changeSubjectDir(subjectDir);
	}

	public MyImages() {
		int i;

		// init greyscale colourmap
		for (i = 0; i < cmap[0].length / 3; i++) {
			cmap[0][3 * i + 0] = i;
			cmap[0][3 * i + 1] = i;
			cmap[0][3 * i + 2] = i;
		}

		// init segmentation label colourmap
		for (QCApp.RegionColor regionColor : QCApp.colorLUT) {
			int No = regionColor.No;
            if (No >= 0 && No < 255) {
                cmap[1][3 * No + 0] = regionColor.R;
                cmap[1][3 * No + 1] = regionColor.G;
                cmap[1][3 * No + 2] = regionColor.B;
            }
		}

		// init image list
		String tmpList[] = { "brain.m0.2D.X", "brain.m0.2D.Y", "brain.m0.2D.Z", "aseg.m1.2D.X", "aseg.m1.2D.Y",
				"aseg.m1.2D.Z", "orig.m0.2D.X", "orig.m0.2D.Y", "orig.m0.2D.Z" };
		imgList = new String[tmpList.length];
		for (i = 0; i < tmpList.length; i++)
			imgList[i] = tmpList[i];

		this.setPreferredSize(new Dimension(800, 512));
		this.addMouseListener(new MouseAdapter() {
			public void mouseClicked(MouseEvent e) {
				mouseDownOnImage(e);
			}
		});
		this.addMouseMotionListener(new MouseAdapter() {
			public void mouseDragged(MouseEvent e) {
				mouseDraggedOnImage(e);
			}
		});
		this.addMouseMotionListener(new MouseAdapter() {
			public void mouseMoved(MouseEvent e) {
				mouseMovedOnImage(e);
			}
		});
		this.addMouseListener(new MouseAdapter() {
			public void mouseReleased(MouseEvent e) {
				mouseMovedOnImage(e);
			}
		});
		this.addMouseWheelListener(new MouseWheelListener() {
			public void mouseWheelMoved(MouseWheelEvent e) {
				mouseWheelRotatedOnImage(e);
		    }
		});
	}
	
	public void renew() {
		selectedImage = 0;
		initialized = 0;
		repaint();
	}

	public void changeSubjectDir(String subjectDir) {
		float tmp[] = new float[3], tmpd[] = new float[3];
		int dim1[] = new int[3];
		int[][] bounds = new int[3][2];
		float[][] invS = new float[3][3];
		
		this.subjectDir = subjectDir;
		volumes = new MyVolumes(subjectDir + "/mri");
		
		// Set selected slices at the center of the segmentation
		MyVolume vol = volumes.getVolume("aseg");
		
		invMat(invS, vol.S);
		
		// find dimension of volume
		tmp[0] = vol.dim[0] - 1;
		tmp[1] = vol.dim[1] - 1;
		tmp[2] = vol.dim[2] - 1;
		multMatVec(tmpd, invS, tmp);
		dim1[0] = Math.round(tmpd[0]);
		dim1[1] = Math.round(tmpd[1]);
		dim1[2] = Math.round(tmpd[2]);

		// find bounding box
		tmp[0] = vol.boundingBox[1][0];
		tmp[1] = vol.boundingBox[1][2];
		tmp[2] = vol.boundingBox[1][4];
		multMatVec(tmpd, invS, tmp);
		bounds[0][0] = Math.round(tmpd[0]);
		bounds[1][0] = Math.round(tmpd[1]);
		bounds[2][0] = Math.round(tmpd[2]);
		tmp[0] = vol.boundingBox[1][1];
		tmp[1] = vol.boundingBox[1][3];
		tmp[2] = vol.boundingBox[1][5];
		multMatVec(tmpd, invS, tmp);
		bounds[0][1] = Math.round(tmpd[0]);
		bounds[1][1] = Math.round(tmpd[1]);
		bounds[2][1] = Math.round(tmpd[2]);
		
		selectedSlice[0] = ((bounds[0][0] + bounds[0][1]) / 2 - Math.min(dim1[0], 0)) / (float) Math.abs(dim1[0]);
		selectedSlice[1] = ((bounds[1][0] + bounds[1][1]) / 2 - Math.min(dim1[1], 0)) / (float) Math.abs(dim1[1]);
		selectedSlice[2] = ((bounds[2][0] + bounds[2][1]) / 2 - Math.min(dim1[2], 0)) / (float) Math.abs(dim1[2]);
		
		setImages(true);
	}

	private void mouseDownOnImage(MouseEvent e) {
		int i;

		if (selectedImage == 0) {
			// Open selected image
			for (i = 0; i < img.length; i++) {
				if (rect[i].contains(e.getPoint())) {
					selectedImage = i + 1;
					toggle = true;
					break;
				}
			}
			setImages();
		} else {
			// Check for button click
			Dimension d = getParent().getSize();
			Rectangle backRect = new Rectangle(d.width - 10 - 48, 10, 48, 20);
			if (backRect.contains(e.getPoint())) {
				// back button is clicked
				selectedImage = 0;
				setImages();
			}
			Rectangle toggleRect = new Rectangle(10, 10, 60, 20);
			if (toggleRect.contains(e.getPoint())) {
				// toggle button is clicked
				toggle = !toggle;
				setImages();
				repaint();
			}
		}
	}

	private void mouseDraggedOnImage(MouseEvent e) {
		if (prevHeight != 0) {
			int i = prevPlane;
			selectedSlice[i] = prevSlice + (float) ((e.getY() - yprev) / prevHeight);
			if (selectedSlice[i] < 0)
				selectedSlice[i] = 0;
			if (selectedSlice[i] > 1)
				selectedSlice[i] = 1;
			setImages();
			repaint();
		}
	}
	
	int counter = 0;

	private void mouseMovedOnImage(MouseEvent e) {
		yprev = e.getY();
		prevHeight = 0;
		
		if (rect == null)
			return;

		if (selectedImage == 0)
			for (int i = 0; i < rect.length; i++) {
				if (rect[i].contains(e.getPoint())) {
					prevHeight = rect[i].getHeight();
					prevPlane = getPlane(imgList[i]);
				}
			}
		else {
			prevHeight = this.getHeight();
			prevPlane = getPlane(imgList[selectedImage - 1]);
		}
		prevSlice = selectedSlice[prevPlane];
	}
	

	private void mouseWheelRotatedOnImage(MouseWheelEvent e) {
		int i;
		float[][] invS = new float[3][3];
		float[] tmp = new float [3];
		float[] tmpd = new float [3];
	    int steps = -e.getWheelRotation();
		float[] dim1 = new float[3];
		float dim;
	    
		if (selectedImage == 0)
			for (i = 0; i < rect.length; i++) {
				if (rect[i].contains(e.getPoint()))
					break;
			}
		else
			i = selectedImage - 1;

		String volName = getVolumeName(imgList[i]);
		MyVolume vol = volumes.getVolume(volName);
		
		invMat(invS, vol.S);
		
		// find dimension of volume
		tmp[0] = vol.dim[0] - 1;
		tmp[1] = vol.dim[1] - 1;
		tmp[2] = vol.dim[2] - 1;
		multMatVec(tmpd, invS, tmp);
		dim1[0] = tmpd[0];
		dim1[1] = tmpd[1];
		dim1[2] = tmpd[2];
		
		dim = Math.abs(dim1[prevPlane]);
		selectedSlice[prevPlane] = prevSlice + steps / dim;
		if (selectedSlice[prevPlane] < 0)
			selectedSlice[prevPlane] = 0;
		if (selectedSlice[prevPlane] > 1)
			selectedSlice[prevPlane] = 1;
		setImages();
		prevSlice = selectedSlice[prevPlane];
	}

	private static void multMatVec(float[] rV, float[][] M, float[] V) {
		rV[0] = M[0][0] * V[0] + M[0][1] * V[1] + M[0][2] * V[2];
		rV[1] = M[1][0] * V[0] + M[1][1] * V[1] + M[1][2] * V[2];
		rV[2] = M[2][0] * V[0] + M[2][1] * V[1] + M[2][2] * V[2];
	}

	private static void multMat(float[][] P, float[][] M, float[][] N) {
		P[0][0] = M[0][0] * N[0][0] + M[0][1] * N[1][0] + M[0][2] * N[2][0];
		P[0][1] = M[0][0] * N[0][1] + M[0][1] * N[1][1] + M[0][2] * N[2][1];
		P[0][2] = M[0][0] * N[0][2] + M[0][1] * N[1][2] + M[0][2] * N[2][2];
		P[1][0] = M[1][0] * N[0][0] + M[1][1] * N[1][0] + M[1][2] * N[2][0];
		P[1][1] = M[1][0] * N[0][1] + M[1][1] * N[1][1] + M[1][2] * N[2][1];
		P[1][2] = M[1][0] * N[0][2] + M[1][1] * N[1][2] + M[1][2] * N[2][2];
		P[2][0] = M[2][0] * N[0][0] + M[2][1] * N[1][0] + M[2][2] * N[2][0];
		P[2][1] = M[2][0] * N[0][1] + M[2][1] * N[1][1] + M[2][2] * N[2][1];
		P[2][2] = M[2][0] * N[0][2] + M[2][1] * N[1][2] + M[2][2] * N[2][2];
	}

	private static float detMat(float[][] M) {
		return M[0][0] * M[1][1] * M[2][2] + M[1][0] * M[2][1] * M[0][2] + M[2][0] * M[0][1] * M[1][2]
				- M[2][0] * M[1][1] * M[0][2] - M[0][0] * M[2][1] * M[1][2] - M[1][0] * M[0][1] * M[2][2];
	}

	private static void invMat(float[][] rM, float[][] M) {
		float d = detMat(M);
		
		rM[0][0] = (M[1][1] * M[2][2] - M[2][1] * M[1][2]) / d;
		rM[0][1] = (M[2][1] * M[0][2] - M[0][1] * M[2][2]) / d;
		rM[0][2] = (M[0][1] * M[1][2] - M[1][1] * M[0][2]) / d;
		rM[1][0] = (M[2][0] * M[1][2] - M[1][0] * M[2][2]) / d;
		rM[1][1] = (M[0][0] * M[2][2] - M[2][0] * M[0][2]) / d;
		rM[1][2] = (M[1][0] * M[0][2] - M[0][0] * M[1][2]) / d;
		rM[2][0] = (M[1][0] * M[2][1] - M[2][0] * M[1][1]) / d;
		rM[2][1] = (M[2][0] * M[0][1] - M[0][0] * M[2][1]) / d;
		rM[2][2] = (M[0][0] * M[1][1] - M[1][0] * M[0][1]) / d;
	}

	static int value2rgb(int v, int cmapindex) {
		int rgb = 0;
		int r, g, b;

//		try {
			r = cmap[cmapindex][3 * v + 0];
			g = cmap[cmapindex][3 * v + 1];
			b = cmap[cmapindex][3 * v + 2];
			rgb = r << 16 | g << 8 | b;
//		} catch (Exception e) {
//			System.out.println("missing label:" + v);
//		}

		return rgb;
	}

	private BufferedImage drawSlice(MyVolume vol, float[] selectedSlice, int plane, int cmapindex) throws Exception
	// draw slice with position 't' in the plane 'plane' at position ox, oy
	// using colourmap 'cmapindex'
	{
		BufferedImage theImg;
		int x, y, z;
		int x1, y1, z1;
		int rgb;
		int v;
		float sliceMax = 0; // maximum slice value
		float slice;
		float[][] P, invP = new float[3][3]; // view plane transformation matrix and its inverse
		float[][] T = new float[3][3], invT = new float[3][3]; // combined transformation matrix and its inverse
		float tmp[] = new float[3], tmpd[] = new float[3], tmpx[] = new float[3];
		int dim1[] = new int[3];
		float pixdim1[] = new float[3];
		Rectangle rect = new Rectangle(0, 0, 1, 1);
		int[][] bounds = new int[2][2];

		// transform volume to view plane
		switch (plane) {
		case 0:
			P = X;
			break;
		case 1:
			P = Y;
			break;
		case 2:
			P = Z;
			break;
		default:
			throw new Exception("No plane selected");
		}
		
		invMat(invP, P);
		multMat(invT, vol.S, invP);
		invMat(T, invT);
		
		// find dimension of volume
		tmp[0] = vol.dim[0] - 1;
		tmp[1] = vol.dim[1] - 1;
		tmp[2] = vol.dim[2] - 1;
		multMatVec(tmpd, T, tmp);
		dim1[0] = Math.round(tmpd[0]);
		dim1[1] = Math.round(tmpd[1]);
		dim1[2] = Math.round(tmpd[2]);

		// find dimension of pixels
		tmp[0] = vol.pixdim[0];
		tmp[1] = vol.pixdim[1];
		tmp[2] = vol.pixdim[2];
		multMatVec(tmpd, T, tmp);
		pixdim1[0] = Math.abs(tmpd[0]);
		pixdim1[1] = Math.abs(tmpd[1]);
		pixdim1[2] = Math.abs(tmpd[2]);

		// find bounding box
		tmp[0] = vol.boundingBox[cmapindex][0];
		tmp[1] = vol.boundingBox[cmapindex][2];
		tmp[2] = vol.boundingBox[cmapindex][4];
		multMatVec(tmpd, T, tmp);
		bounds[0][0] = Math.round(tmpd[0]);
		bounds[1][0] = Math.round(tmpd[1]);
		tmp[0] = vol.boundingBox[cmapindex][1];
		tmp[1] = vol.boundingBox[cmapindex][3];
		tmp[2] = vol.boundingBox[cmapindex][5];
		multMatVec(tmpd, T, tmp);
		bounds[0][1] = Math.round(tmpd[0]);
		bounds[1][1] = Math.round(tmpd[1]);

		rect.x = Math.min(bounds[0][0], bounds[0][1]);
		rect.y = Math.min(bounds[1][0], bounds[1][1]);
		rect.width = Math.max(bounds[0][0], bounds[0][1]) - rect.x + 1;
		rect.height = Math.max(bounds[1][0], bounds[1][1]) - rect.y + 1;
		
		// find selected slice corresponding to z
		tmp[0] = selectedSlice[0];
		tmp[1] = selectedSlice[1];
		tmp[2] = selectedSlice[2];
		multMatVec(tmpd, P, selectedSlice);
		slice = Math.abs(tmpd[2]);
		z = Math.round((slice * Math.abs(dim1[2])) + Math.min(dim1[2], 0));

		// find maximum brightness
		for (x = rect.x; x < rect.width + rect.x; x++)
			for (y = rect.y; y < rect.height + rect.y; y++) {
				tmp[0] = x;
				tmp[1] = y;
				tmp[2] = z;
				multMatVec(tmpx, invT, tmp);
				x1 = Math.round(tmpx[0]);
				y1 = Math.round(tmpx[1]);
				z1 = Math.round(tmpx[2]);
				
				v = Math.round(vol.getValue(x1, y1, z1));
				if (v > sliceMax)
					sliceMax = v;
			}

		// draw slice
		theImg = new BufferedImage(rect.width, rect.height, BufferedImage.TYPE_INT_RGB);
		for (x = 0; x < rect.width; x++)
			for (y = 0; y < rect.height; y++) {
				tmp[0] = x + rect.x;
				tmp[1] = y + rect.y;
				tmp[2] = z;
				multMatVec(tmpx, invT, tmp);
				x1 = Math.round(tmpx[0]);
				y1 = Math.round(tmpx[1]);
				z1 = Math.round(tmpx[2]);

				v = Math.round(vol.getValue(x1, y1, z1));
				if (cmapindex == 0)
					rgb = value2rgb((int) (v * 255.0 / sliceMax), cmapindex);
				else
					rgb = value2rgb(v, cmapindex);
				if (rgb > 0)
					theImg.setRGB(x, y, rgb);
			}

		// scale
		BufferedImage scaledImg = new BufferedImage(Math.round(rect.width * pixdim1[0] + 10),
				Math.round(rect.height * pixdim1[1] + 10), BufferedImage.TYPE_INT_RGB);
		AffineTransform at = new AffineTransform();
		at.scale(pixdim1[0], pixdim1[1]);
		at.translate(5, 5);
		AffineTransformOp scaleOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
		return scaleOp.filter(theImg, scaledImg);
	}

	private BufferedImage drawSlice(MyVolume vol, MyVolume volBack, float[] selectedSlice, int plane, int cmapindex,
			Boolean toggle) throws Exception
	// draw slice with position 't' in the plane 'plane' at position ox, oy
	// using colourmap 'cmapindex'
	{
		BufferedImage theImg;
		int x, y, z;
		int x1, y1, z1;
		int rgb, rgb0;
		int v, v0;
		float sliceMax = 0; // maximum slice value
		float slice;
		float[][] P, invP = new float[3][3];	// view plane transformation matrix and its inverse
		float[][] T = new float[3][3], invT = new float[3][3]; // combined transformation matrix and its inverse
		float tmp[] = new float[3], tmpd[] = new float[3], tmpx[] = new float[3];
		int dim1[] = new int[3];
		float pixdim1[] = new float[3];
		Rectangle rect = new Rectangle(0, 0, 1, 1);
		int[][] bounds = new int[2][2];

		// transform volume to view plane
		switch (plane) {
		case 0:
			P = X;
			break;
		case 1:
			P = Y;
			break;
		case 2:
			P = Z;
			break;
		default:
			throw new Exception("No plane selected");
		}
		
		invMat(invP, P);
		multMat(invT, vol.S, invP);
		invMat(T, invT);
		
		tmp[0] = volBack.dim[0] - 1;
		tmp[1] = volBack.dim[1] - 1;
		tmp[2] = volBack.dim[2] - 1;
		multMatVec(tmpd, T, tmp);
		dim1[0] = Math.round(tmpd[0]);
		dim1[1] = Math.round(tmpd[1]);
		dim1[2] = Math.round(tmpd[2]);

		tmp[0] = vol.pixdim[0];
		tmp[1] = vol.pixdim[1];
		tmp[2] = vol.pixdim[2];
		multMatVec(tmpd, T, tmp);
		pixdim1[0] = Math.abs(tmpd[0]);
		pixdim1[1] = Math.abs(tmpd[1]);
		pixdim1[2] = Math.abs(tmpd[2]);

		// find bounding box
		tmp[0] = volBack.boundingBox[0][0];
		tmp[1] = volBack.boundingBox[0][2];
		tmp[2] = volBack.boundingBox[0][4];
		multMatVec(tmpd, T, tmp);
		bounds[0][0] = Math.round(tmpd[0]);
		bounds[1][0] = Math.round(tmpd[1]);
		tmp[0] = volBack.boundingBox[0][1];
		tmp[1] = volBack.boundingBox[0][3];
		tmp[2] = volBack.boundingBox[0][5];
		multMatVec(tmpd, T, tmp);
		bounds[0][1] = Math.round(tmpd[0]);
		bounds[1][1] = Math.round(tmpd[1]);

		rect.x = Math.min(bounds[0][0], bounds[0][1]);
		rect.y = Math.min(bounds[1][0], bounds[1][1]);
		rect.width = Math.max(bounds[0][0], bounds[0][1]) - rect.x + 1;
		rect.height = Math.max(bounds[1][0], bounds[1][1]) - rect.y + 1;
		
		// find selected slice corresponding to z
		tmp[0] = selectedSlice[0];
		tmp[1] = selectedSlice[1];
		tmp[2] = selectedSlice[2];
		multMatVec(tmpd, P, selectedSlice);
		slice = Math.abs(tmpd[2]);
		z = Math.round(slice * Math.abs(dim1[2])) + Math.min(dim1[2], 0);

		// find maximum brightness
		for (x = rect.x; x < rect.width + rect.x; x++)
			for (y = rect.y; y < rect.height + rect.y; y++) {
				tmp[0] = x;
				tmp[1] = y;
				tmp[2] = z;
				multMatVec(tmpx, invT, tmp);
				x1 = Math.round(tmpx[0]);
				y1 = Math.round(tmpx[1]);
				z1 = Math.round(tmpx[2]);

				v = Math.round(volBack.getValue(x1, y1, z1));
				if (v > sliceMax)
					sliceMax = v;
			}

		// draw slice
		theImg = new BufferedImage(rect.width, rect.height, BufferedImage.TYPE_INT_RGB);
		for (x = 0; x < rect.width; x++)
			for (y = 0; y < rect.height; y++) {
				tmp[0] = x + rect.x;
				tmp[1] = y + rect.y;
				tmp[2] = z;
				multMatVec(tmpx, invT, tmp);
				x1 = Math.round(tmpx[0]);
				y1 = Math.round(tmpx[1]);
				z1 = Math.round(tmpx[2]);

				v = Math.round(vol.getValue(x1, y1, z1));
				v0 = Math.round(volBack.getValue(x1, y1, z1));
				rgb = value2rgb(v, cmapindex);
				rgb0 = value2rgb((int) Math.round(v0 * 255.0 / sliceMax), 0);
				if (rgb > 0 && toggle)
					theImg.setRGB(x, y, rgb);
				else
					theImg.setRGB(x, y, rgb0);
			}
		
		// scale
		BufferedImage scaledImg = new BufferedImage(Math.round(rect.width * pixdim1[0] + 10),
				Math.round(rect.height * pixdim1[1] + 10), BufferedImage.TYPE_INT_RGB);
		AffineTransform at = new AffineTransform();
		at.scale(pixdim1[0], pixdim1[1]);
		at.translate(5, 5);
		AffineTransformOp scaleOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
		return scaleOp.filter(theImg, scaledImg);
	}

	private BufferedImage drawVolume(MyVolume vol, int plane, int cmapindex) throws Exception {
		BufferedImage theImg;
		int x, y, z, x1, y1, z1, rgb;
		int s0, s1;
		int v;
		float[][] P, invP = new float[3][3];
		float[][] T = new float[3][3], invT = new float[3][3];
		float[] tmp = new float[3], tmpd = new float[3], tmpx = new float[3];
		float[] pixdim1 = new float[3];
		int r, g, b;
		Rectangle rect = new Rectangle(0, 0, 0, 0);
		int[][] bounds = new int[3][2];

		// transform volume to view plane
		switch (plane) {
		case 0:
			P = X;
			break;
		case 1:
			P = Y;
			break;
		case 2:
			P = Z;
			break;
		default:
			throw new Exception("No plane selected");
		}
		
		invMat(invP, P);
		multMat(invT, vol.S, invP);
		invMat(T, invT);

		tmp[0] = vol.pixdim[0];
		tmp[1] = vol.pixdim[1];
		tmp[2] = vol.pixdim[2];
		multMatVec(tmpd, T, tmp);
		pixdim1[0] = Math.abs(tmpd[0]);
		pixdim1[1] = Math.abs(tmpd[1]);
		pixdim1[2] = Math.abs(tmpd[2]);

		// find 1st and last non-empty slices (for lighting) and bounding box
		tmp[0] = vol.boundingBox[cmapindex][0];
		tmp[1] = vol.boundingBox[cmapindex][2];
		tmp[2] = vol.boundingBox[cmapindex][4];
		multMatVec(tmpd, T, tmp);
		bounds[0][0] = Math.round(tmpd[0]);
		bounds[1][0] = Math.round(tmpd[1]);
		bounds[2][0] = Math.round(tmpd[2]);
		tmp[0] = vol.boundingBox[cmapindex][1];
		tmp[1] = vol.boundingBox[cmapindex][3];
		tmp[2] = vol.boundingBox[cmapindex][5];
		multMatVec(tmpd, T, tmp);
		bounds[0][1] = Math.round(tmpd[0]);
		bounds[1][1] = Math.round(tmpd[1]);
		bounds[2][1] = Math.round(tmpd[2]);

		rect.x = Math.min(bounds[0][0], bounds[0][1]);
		rect.y = Math.min(bounds[1][0], bounds[1][1]);
		rect.width = Math.max(bounds[0][0], bounds[0][1]) - rect.x + 1;
		rect.height = Math.max(bounds[1][0], bounds[1][1]) - rect.y + 1;
		
		s0 = Math.min(bounds[2][0], bounds[2][1]); // 1st
		s1 = Math.max(bounds[2][0], bounds[2][1]); // last

		// draw volume
		theImg = new BufferedImage(rect.width, rect.height, BufferedImage.TYPE_INT_RGB);
		for (z = s0; z <= s1; z++)
			for (x = 0; x < rect.width; x++)
				for (y = 0; y < rect.height; y++) {
					tmp[0] = x + rect.x;
					tmp[1] = y + rect.y;
					tmp[2] = z;
					multMatVec(tmpx, invT, tmp);
					x1 = Math.round(tmpx[0]);
					y1 = Math.round(tmpx[1]);
					z1 = Math.round(tmpx[2]);

					v = Math.round(vol.getValue(x1, y1, z1));
					rgb = value2rgb(v, cmapindex); // if(rgb==0)
													// System.out.println("missing
													// label:"+v);
					if (rgb == 0)
						continue;

					// light
					r = (int) ((rgb >> 16) * Math.pow((z - s0) / (s1 - s0), 0.5));
					g = (int) (((rgb & 0xff00) >> 8) * Math.pow((z - s0) / (s1 - s0), 0.5));
					b = (int) ((rgb & 0xff) * Math.pow((z - s0) / (s1 - s0), 0.5));
					rgb = r << 16 | g << 8 | b;

					theImg.setRGB(x, y, rgb);
				}
		// scale
		BufferedImage scaledImg = new BufferedImage(Math.round(rect.width * pixdim1[0] + 10),
				Math.round(rect.height * pixdim1[1] + 10), BufferedImage.TYPE_INT_RGB);
		AffineTransform at = new AffineTransform();
		at.scale(pixdim1[0], pixdim1[1]);
		at.translate(5, 5);
		AffineTransformOp scaleOp = new AffineTransformOp(at, AffineTransformOp.TYPE_BILINEAR);
		return scaleOp.filter(theImg, scaledImg);
	}

	private BufferedImage drawErrorSlice() {
		BufferedImage theImg = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
		Graphics2D g2 = theImg.createGraphics();

		g2.setFont(new Font("Helvetica", Font.BOLD, 14));
		g2.drawString("UNAVAILABLE", 15, 64);
		g2.drawRect(1, 1, 126, 126);

		return theImg;
	}

	private String getVolumeName(String name) {
		return name.substring(0, name.length() - 8);
	}

	private String getPlaneName(String name) {
		return name.substring(name.length() - 1, name.length());
	}
	
	private int getPlane(String name) {
		String volPlane = getPlaneName(name);
		int plane = -1;
		if (volPlane.equals("X"))
			plane = 0;
		if (volPlane.equals("Y"))
			plane = 1;
		if (volPlane.equals("Z"))
			plane = 2;
		
		return plane;
	}

	private String getImageTypeName(String name) {
		return name.substring(name.length() - 4, name.length() - 2);
	}

	private int getCMapIndex(String name) {
		return Integer.parseInt(name.substring(name.length() - 6, name.length() - 5));
	}

	public void paint(Graphics g) {
		Dimension dim = this.getSize();
		g.setColor(Color.black);
		g.fillRect(0, 0, dim.width, dim.height);

		if (initialized == 0)
			return;

		if (rect == null)
			rect = new Rectangle[img.length];

		if (selectedImage == 0) {
			// All images view
			int i;
			int xoff = 0, yoff = 0, maxHeight;
			double z = 2; // zoom

			maxHeight = 0;
			for (i = 0; i < img.length; i++) {
				if (xoff + z * img[i].getWidth() >= this.getParent().getSize().width) {
					xoff = 0;
					yoff += maxHeight;
					maxHeight = 0;
				}
				g.drawImage(img[i], xoff, yoff, (int) (z * img[i].getWidth()), (int) (z * img[i].getHeight()), null);
				rect[i] = new Rectangle(xoff, yoff, (int) (z * img[i].getWidth()), (int) (z * img[i].getHeight()));
				xoff += (int) (z * img[i].getWidth());
				if (z * img[i].getHeight() > maxHeight)
					maxHeight = (int) (z * img[i].getHeight());
			}

			// adjust image size for scroll
			Dimension d = new Dimension(this.getParent().getSize().width, yoff + maxHeight);
			if (!d.equals(this.getParent().getSize())) {
				this.setPreferredSize(d);
				this.revalidate();
			}
		} else {
			// Single volume view

			// adjust image size for scroll
			Dimension d = this.getParent().getSize();
			if (!d.equals(this.getSize())) {
				this.setPreferredSize(d);
				this.revalidate();
				return;
			}

			// draw image
			double scale = this.getHeight() / (double) img0.getHeight();
			int xoff, yoff;

			xoff = (int) ((this.getWidth() - img0.getWidth() * scale) / 2.0);
			yoff = 0;
			g.drawImage(img0, xoff, yoff, (int) (scale * img0.getWidth()), (int) (scale * img0.getHeight()), null);

			g.setColor(Color.white);
			g.drawRoundRect(d.width - 10 - 48, 10, 48, 20, 15, 15);
			String back = "BACK";
			FontMetrics fm = this.getFontMetrics(this.getFont());
			int width = fm.stringWidth(back);
			int height = fm.getHeight();
			g.drawString("BACK", d.width - 10 - 48 + (48 - width) / 2, 10 + 20 - (20 - height) / 2 - 3);

			g.setColor(Color.white);
			g.drawRoundRect(10, 10, 60, 20, 15, 15);
			String toggleStr = toggle ? "HIDE" : "SHOW";
			fm = this.getFontMetrics(this.getFont());
			width = fm.stringWidth(toggleStr);
			g.drawString(toggleStr, 10 + (60 - width) / 2, 10 + 20 - (20 - height) / 2 - 3);
		}
	}
	
	public int setImages() {
		return setImages(false);
	}

	public int setImages(boolean init) {
		File f = null; // to avoid the not initialized error
		int i;
		String volName = "";
		MyVolume vol;
		MyVolume volBack;
		int err = 0;

		if (selectedImage == 0) {
			// All images view
			img = new BufferedImage[imgList.length];
			for (i = 0; i < imgList.length; i++) {
				if (init) {
					String name = subjectDir + "/qc/" + imgList[i] + ".png";
					f = new File(name);
					if (f.exists()) {
						// QC images available: load them
						QCApp.printStatusMessage("Loading image \"" + name + "\"...");
						try {
							img[i] = ImageIO.read(f);
							continue;
						} catch (IOException e) {}
					}
				}
				
				// QC images unavailable: make them (and save them)
				volName = getVolumeName(imgList[i]);
				vol = volumes.getVolume(volName);
				if (vol.volume == null) {
					QCApp.printStatusMessage(
							"ERROR: Volume \"" + volumes.directory + "/" + volName + ".mgz\" unavailable.");
					img[i] = drawErrorSlice();
					err = 1;
				} else {
					String volPlane = getPlaneName(imgList[i]);
					int plane = getPlane(imgList[i]);
					String imgType = getImageTypeName(imgList[i]);
					int cmapindex;

					QCApp.printStatusMessage("Drawing volume \"" + volName + "\", plane:" + volPlane + "...");

					cmapindex = getCMapIndex(imgList[i]);

					try {
						if (imgType.equals("2D"))
							img[i] = drawSlice(vol, selectedSlice, plane, cmapindex);
						else
							img[i] = drawVolume(vol, plane, cmapindex);
						
						if (init) {
							// save image (create directory qc if it does not exist)
							File qcdir =  new File(subjectDir + "/qc");
							if (!qcdir.exists())
								qcdir.mkdir();
							ImageIO.write(img[i], "png", f);
						}
					} catch (Exception e) {
						err = 1;
						return err;
					} finally {
						QCApp.printStatusMessage("");
					}
				}
			}
		} else {
			// Single volume view
			i = selectedImage - 1;

			// load volume
			volName = getVolumeName(imgList[i]);
			vol = volumes.getVolume(volName);
			int plane = getPlane(imgList[i]);
			int cmapindex = getCMapIndex(imgList[i]);

			try {
				if (cmapindex > 0) {
					volBack = volumes.getVolume("orig");
					img0 = drawSlice(vol, volBack, selectedSlice, plane, cmapindex, toggle);
				} else
					img0 = drawSlice(vol, selectedSlice, plane, cmapindex);
			} catch (Exception e) {
				err = 1;
				return err;
			} finally {
				QCApp.printStatusMessage("");
			}
		}

		initialized = 1;
		repaint();

		return err;
	}

}

class MyGraphs extends JComponent {
	private static final long serialVersionUID = -6147931626622313147L;
	static int NB_REGIONS; // The number of brain regions
	static List<String> regions = new ArrayList<String>();

	private double[] mean, std, selectedSubjectVolumes;
	private File subjectsDir;
	
	public MyGraphs() {
		NB_REGIONS = QCApp.colorLUT.size() + 2;
		regions.add("IntraCranialVol");
		regions.add("BrainVol");
		for (QCApp.RegionColor regionColor : QCApp.colorLUT) {
			regions.add(regionColor.label);
		}
		mean = new double[NB_REGIONS];
		std = new double[NB_REGIONS];
		selectedSubjectVolumes = new double[NB_REGIONS];
	}

	public void paint(Graphics g) {
		Graphics2D g2 = (Graphics2D) g;
		float val;
		int i, x[] = new int[NB_REGIONS+1];
		Dimension dim = this.getSize();
		Stroke dashed = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 5.0f, new float[] { 5.0f },
				0.0f);

		for (i = 0; i <= NB_REGIONS; i++)
			x[i] = (int) ((dim.width - 1) * i / (double) NB_REGIONS);

		// draw brain structure bars, with colours depending on selected-subject
		// values
		g2.setColor(Color.black);
		for (i = 0; i < NB_REGIONS; i++) {
			if (selectedSubjectVolumes[0] != 0) {
				val = (float) ((selectedSubjectVolumes[i] - mean[i]) / (2.0 * std[i]));
				if (val >= 0 && val <= 1)
					g2.setColor(new Color(val, 1.0f - val, 0.0f));
				else if (val >= -1 && val < 0)
					g2.setColor(new Color(0.0f, 1.0f + val, -val));
				else
					g2.setColor(Color.white);
			} else
				g2.setColor(Color.white);
			g2.fillRect(x[i], 0, x[i + 1], dim.height);
			g2.setColor(Color.black);
			g2.drawRect(x[i], 0, x[i + 1], dim.height);
		}

		// draw dots for selected subject values
		g2.setColor(Color.black);
		if (selectedSubjectVolumes[0] != 0)
			for (i = 0; i < NB_REGIONS; i++) {
				val = (float) (0.5f + (selectedSubjectVolumes[i] - mean[i]) / (2.0 * std[i]) / 2.0);
				if (val < 0)
					val = 0;
				if (val > 1)
					val = 1;
				g2.fillOval((x[i] + x[i + 1]) / 2 - 5, (int) (dim.height * (1 - val)) - 5, 11, 11);
			}

		// draw mean and +/- 1 std values
		g2.setColor(Color.black);
		g2.drawLine(0, dim.height / 2, dim.width, dim.height / 2);
		g2.setStroke(dashed);
		g2.drawLine(0, dim.height / 4, dim.width, dim.height / 4);
		g2.drawLine(0, dim.height * 3 / 4, dim.width, dim.height * 3 / 4);
		
		// to cope with a MAC OS X bug
		FontRenderContext frc = new FontRenderContext(g2.getTransform(), true, true);

		// draw brain structure names
		for (i = 0; i < NB_REGIONS; i++) {
//			g2.translate((x[i] + x[i + 1]) / 2, 0);
			g2.rotate(Math.PI / 2.0);
//			g2.drawString(regions[i], 20, -(x[i] + x[i + 1]) / 2);
			g2.drawGlyphVector(g2.getFont().createGlyphVector(frc, regions.get(i)), 5, -(x[i] + x[i + 1]) / 2);
			g2.rotate(-Math.PI / 2.0);
//			g2.translate(-(x[i] + x[i + 1]) / 2, 50);
		}
	}

	public int getVolumesForSubject(String subject, double x[]) {
		BufferedReader input;
		int i;
		int err = 0;
		for (i = 0; i < x.length; i++)
			x[i] = 0;
		
		try {
			input = new BufferedReader(new FileReader(subjectsDir + "/" + subject + "/stats/aseg.stats"));
			String line;
			String[] parts;
			while ((line = input.readLine()) != null) {
				if (line.startsWith("#")) {
					// Load ICV and BrainSeg data
					if (line.startsWith("# Measure")) {
						parts = line.substring(10).trim().split(", ");
						if ((parts[0].equals("EstimatedTotalIntraCranialVol") || parts[0].equals(regions.get(0))) && parts[4].equals("mm^3"))
							x[0] = Float.valueOf(parts[3]);
						if ((parts[0].equals("BrainSeg") || parts[0].equals(regions.get(1))) && parts[4].equals("mm^3"))
							x[1] = Float.valueOf(parts[3]);
					}
				}
				// Segmented Data
				else {
					parts = line.trim().split(" +");
					i = regions.indexOf(parts[4]);
					if (i > -1)
						x[i] = Float.valueOf(parts[3]);
				}
			}
			input.close();
		} catch (IOException e) {
			err = 1;
			return err;
		} catch (InputMismatchException e) {
			err = 1;
			return err;
		}
		return err;
	}

	public void configure(File subjectsDir, List<String> subjects) {
		double s0 = 0;
		double s1[] = new double[NB_REGIONS];
		double s2[] = new double[NB_REGIONS];
		double x[] = new double[NB_REGIONS];
		int i;
		int j;
		int err;

		this.subjectsDir = subjectsDir;

		for (i=0; i<subjects.size(); i++) {
			QCApp.printStatusMessage("Configuring stat graphs... " + (i + 1) + "/" + subjects.size());
			err = getVolumesForSubject(subjects.get(i), x);
			if (err == 1) {
				QCApp.setQC(subjects.get(i), "Segmentation results unavailable");
				continue;
			}

			for (j = 0; j < NB_REGIONS; j++) {
				s1[j] += x[j];
				s2[j] += x[j] * x[j];
			}
			s0++;
		}
		for (j = 0; j < NB_REGIONS; j++) {
			mean[j] = s1[j] / s0;
			std[j] = Math.sqrt((s0 * s2[j] - s1[j] * s1[j]) / (s0 * (s0 - 1)));
			System.out.println(regions.get(j) + ":\t" + mean[j] + " +- " + std[j]);
		}
	}

	public void setSelectedSubject(String subject) {
		getVolumesForSubject(subject, selectedSubjectVolumes);
		repaint();
	}
}

public class QCApp {
	
	static class RegionColor {
		int No, R, G, B;
		String label;
	}

	static private class MyTableModel extends DefaultTableModel {

		private static final long serialVersionUID = -3092232013219096243L;

		@Override
	    public boolean isCellEditable(int rowIndex, int columnIndex) {
	        
	        //here the columns 0 and 2 are non-editable
	        if (columnIndex == 0 || columnIndex == 2) return false;
	        
	        //the rest is editable
	        return true;
	    }
	}

	private static JFrame f;
	private static JTextArea status;
	private static JButton chooseButton;
	private static JButton saveButton;
	private static JTable table;
	private static MyTableModel model;
	private static MyImages images;
	private static MyGraphs graphs;
	private static File subjectsDir;
	
	public static List<RegionColor> colorLUT;

	public static void printStatusMessage(String msg) {
		if (QCApp.status != null) {
			QCApp.status.setText(msg);
			QCApp.status.paintImmediately(QCApp.status.getVisibleRect());
		} else
			System.out.println(msg);
	}

	public static void selectSubject(JTable table) {
		if (table.getSelectedRowCount() == 0)
			return;
		int i = table.getSelectedRows()[0];
		String subject = model.getValueAt(i, 2).toString();

		images.changeSubjectDir(subjectsDir + "/" + subject);
		printStatusMessage("Subject: " + subject + ".");

		graphs.setSelectedSubject(subject);
	}

	public static void chooseDirectory() {
		List<String> subjects = new ArrayList<String>();

		// select Subjects directory
		final JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("Choose Subjects Directory...");
		fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		int returnVal = fc.showOpenDialog(null);
		if (returnVal != JFileChooser.APPROVE_OPTION)
			return;
		subjectsDir = fc.getSelectedFile();
		printStatusMessage("Subjects Directory: " + subjectsDir + ".");

		// clear table
		model.setRowCount(0);
//		for (int i = model.getRowCount() - 1; i > -1; i--) {
//			model.removeRow(i);
//		}

		// add files to table
		File files[] = subjectsDir.listFiles();
		Arrays.sort(files);
		Vector<Object> row;
		int n = 1;
		int i, j, k, l;
		for (i = 0; i < files.length; i++) {
			if (!new File(files[i] + "/stats/aseg.stats").isFile())
				continue;

			printStatusMessage("Creating QC table... " + (i + 1) + "/" + files.length);

			String subject = files[i].getName();
			subjects.add(subject);

			row = new Vector<Object>();
			row.add(n);
			row.add(0);
			row.add(subject);
			row.add("");
			model.addRow(row);
			n++;
		}
		
		// configure stats graphs
		graphs.configure(subjectsDir, subjects);
		
		// if there is a qc.txt file inside subjectsDir, load it.
		File f = new File(subjectsDir + "/qc.txt");
		if (f.exists()) {
			System.out.println("qc.txt file present, read it.");
			BufferedReader input;
			int qc;
			String sub;
			String comment;
			String line;
			try {
				input = new BufferedReader(new FileReader(f));
				input.readLine(); // skip header row
				
				while ((line = input.readLine()) != null) {
					try {
						j = line.indexOf("\t");
						k = line.indexOf("\t", j + 1);
						l = line.indexOf("\t", k + 1);
						sub = line.substring(0, j);
						qc = Integer.parseInt(line.substring(j + 1, k));
						comment = line.substring(k + 1, l);
						
					    for (i = model.getRowCount() - 1; i >= 0; --i)
				            if (sub.equals(model.getValueAt(i, 2).toString()))
				                // what if value is not unique?
				                break;
						
						if (i < 0) {
							System.out.println("Warning: qc.txt subject " + sub + " not found in subjects directory.");
							printStatusMessage("Warning: qc.txt subject " + sub + " not found in subjects directory.");
							continue;
						}
						model.setValueAt(qc, i, 1);
						model.setValueAt(comment, i, 3);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				input.close();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		saveButton.setVisible(true);
		printStatusMessage(model.getRowCount() + " subjects read.");
		images.renew();
	}

	public static void setQC(String subject, String msg) {
		int i;
		for (i = 0; i < model.getRowCount(); i++)
			if (model.getValueAt(i, 2).toString().equals(subject)) {
				model.setValueAt(0, i, 1);
				model.setValueAt(msg, i, 3);
				break;
			}
	}

	public static void saveQC() {
		// Save QC
		final JFileChooser fc = new JFileChooser();
		fc.setCurrentDirectory(subjectsDir);
		fc.setSelectedFile(new File("qc.txt"));
		fc.setDialogTitle("Save QC File...");
		int returnVal = fc.showSaveDialog(null);
		if (returnVal != JFileChooser.APPROVE_OPTION)
			return;

		File file = fc.getSelectedFile();
		try {
			int i, j;
			double x[] = new double[MyGraphs.NB_REGIONS];
			Writer output = new BufferedWriter(new FileWriter(file));
			String sub;

			output.write("Subject\tQC\tComments\t");
			for (j = 0; j < MyGraphs.NB_REGIONS; j++)
				if (j < MyGraphs.NB_REGIONS - 1)
					output.write(MyGraphs.regions.get(j) + "\t");
				else
					output.write(MyGraphs.regions.get(j) + "\n");

			for (i = 0; i < model.getRowCount(); i++) {
				sub = model.getValueAt(i, 2).toString();
				output.write(sub + "\t"); // Subject
				output.write(model.getValueAt(i, 1).toString() + "\t"); // QC
				output.write(model.getValueAt(i, 3).toString() + "\t"); // Comments
				
				printStatusMessage("Saving volumetric data for subject " + (i + 1) + "/" + model.getRowCount());
				graphs.getVolumesForSubject(sub, x); // Volumes
				for (j = 0; j < MyGraphs.NB_REGIONS; j++)
					if (j < MyGraphs.NB_REGIONS - 1)
						output.write(x[j] + "\t");
					else
						output.write(x[j] + "\n");
			}
			output.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		printStatusMessage("QC file saved.");
	}

	public static void createAndShowGUI() {
		f = new JFrame("QCApp");
		f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		f.addWindowListener(new WindowAdapter() {
			public void windowClosing(WindowEvent e) {
				System.exit(0);
			}
		});

		// Status text
		status = new JTextArea("Choose a Subjects Directory");
		status.setOpaque(false);

		// Choose Button
		chooseButton = new JButton("Choose Subjects Directory...");
		chooseButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				chooseDirectory();
			}
		});

		// Save Button
		saveButton = new JButton("Save QC...");
		saveButton.setVisible(false);
		saveButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				saveQC();
			}
		});

		// Table
		model = new MyTableModel();
		table = new JTable(model);
		model.addColumn("#");
		model.addColumn("QC");
		model.addColumn("Subject");
		model.addColumn("Comments");
		table.setCellSelectionEnabled(true);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setPreferredScrollableViewportSize(new Dimension(250, 70));
		table.setFillsViewportHeight(true);
		table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			public void valueChanged(ListSelectionEvent e) {
				if (!e.getValueIsAdjusting())
					selectSubject(table);
			}
		});
		JScrollPane scrollPane = new JScrollPane(table);
		table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
		table.getColumnModel().getColumn(0).setMinWidth(32);
		table.getColumnModel().getColumn(1).setMinWidth(32);
		table.getColumnModel().getColumn(2).setPreferredWidth(800);
		table.getColumnModel().getColumn(3).setPreferredWidth(800);

		// Graphs
		graphs = new MyGraphs();
		graphs.setPreferredSize(new Dimension(250, 250));

		// Image
		images = new MyImages();
		JScrollPane imagesScrollPane = new JScrollPane(images);

		// Split Pane for Table and Graphs
		JSplitPane splitPaneForTableAndGraphs = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollPane, graphs);
		splitPaneForTableAndGraphs.setOneTouchExpandable(true);
		splitPaneForTableAndGraphs.setDividerLocation(350);
		splitPaneForTableAndGraphs.setResizeWeight(1.0);

		// Split Pane for the previous and Images
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, splitPaneForTableAndGraphs,
				imagesScrollPane);
		splitPane.setOneTouchExpandable(true);
		splitPane.setDividerLocation(350);

		// Layout the GUI
		GroupLayout layout = new GroupLayout(f.getContentPane());
		f.getContentPane().setLayout(layout);
		layout.setAutoCreateGaps(true);
		layout.setAutoCreateContainerGaps(true);
		layout.setHorizontalGroup(layout.createParallelGroup()
				.addGroup(layout.createSequentialGroup().addComponent(chooseButton).addComponent(saveButton))
				.addComponent(splitPane).addComponent(status));
		layout.setVerticalGroup(layout.createSequentialGroup()
				.addGroup(layout.createParallelGroup() // BASELINE
						.addComponent(chooseButton).addComponent(saveButton))
				.addComponent(splitPane).addComponent(status, GroupLayout.PREFERRED_SIZE, GroupLayout.DEFAULT_SIZE,
						GroupLayout.PREFERRED_SIZE));
		f.pack();
		f.setVisible(true);
	}

	public static void main(String[] args) throws NumberFormatException, IOException{
		
		// init segmentation label colourmap
		colorLUT = new ArrayList<RegionColor>();
    	String colorLUTFile = "FreeSurferColorLUT.txt";
    	InputStream input = QCApp.class.getResourceAsStream(colorLUTFile);
    	String line;
    	BufferedReader buffer = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8));
    	while ((line = buffer.readLine()) != null) {
			if (line.startsWith("#"))
				continue;
            String[] parts = line.split("\\s+");
            if (parts.length < 5)
            	continue;
            int No = Integer.valueOf(parts[0]);
            if (No >= 0 && No < 255) {
                RegionColor regionColor = new RegionColor();
                regionColor.No = No;
                regionColor.label = parts[1];
                regionColor.R = Integer.valueOf(parts[2]);
                regionColor.G = Integer.valueOf(parts[3]);
                regionColor.B = Integer.valueOf(parts[4]);
                colorLUT.add(regionColor);
            }
        }
    	
		if (args.length == 1) {
			File dir = new File(args[0]);
			new MyImages(dir.getPath());
		} else {
			new QCApp();
			createAndShowGUI();
		}
	}
}