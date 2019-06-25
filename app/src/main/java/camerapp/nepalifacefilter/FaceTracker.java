package camerapp.nepalifacefilter;

import android.content.Context;
import android.graphics.PointF;

import com.google.android.gms.vision.Tracker;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.Landmark;

import java.util.HashMap;
import java.util.Map;

import camerapp.nepalifacefilter.camera.GraphicOverlay;


public class FaceTracker extends Tracker<Face> {
    private static final String TAG = "FaceTracker";

    private GraphicOverlay mOverlay;
    private Context mContext;
    private boolean mIsFrontFacing;
    private FaceGraphic mFaceGraphic;
    private FaceData mFaceData;
    private String chosenFilter;

    private boolean mPreviousIsLeftEyeOpen = true;
    private boolean mPreviousIsRightEyeOpen = true;

    // Subjects may move too quickly to for the system to detect their detect features,
    // or they may move so their features are out of the tracker's detection range.
    // This map keeps track of previously detected facial landmarks so that we can approximate
    // their locations when they momentarily "disappear".
    private Map<Integer, PointF> mPreviousLandmarkPositions = new HashMap<>();

    FaceTracker(GraphicOverlay overlay, Context context, boolean isFrontFacing, String chosenFilter) {
        mOverlay = overlay;
        mContext = context;
        mIsFrontFacing = isFrontFacing;
        mFaceData = new FaceData();
        this.chosenFilter = chosenFilter;
    }

    // 1
    @Override
    public void onNewItem(int id, Face face) {
        mFaceGraphic = new FaceGraphic(mOverlay, mContext, mIsFrontFacing, chosenFilter);
        mFaceData.setId(id % 4);
    }

    // 2
    @Override
    public void onUpdate(FaceDetector.Detections<Face> detectionResults, Face face) {
        mOverlay.add(mFaceGraphic);

        // Get head angles.
        mFaceData.setEulerY(face.getEulerY());
        mFaceData.setEulerZ(face.getEulerZ());

        // Get face dimensions.
        mFaceData.setPosition(face.getPosition());
        mFaceData.setWidth(face.getWidth());
        mFaceData.setHeight(face.getHeight());

        // Get the positions of facial landmarks.
        updatePreviousLandmarkPositions(face);
        mFaceData.setLeftEyePosition(getLandmarkPosition(face, Landmark.LEFT_EYE));
        mFaceData.setRightEyePosition(getLandmarkPosition(face, Landmark.RIGHT_EYE));
        mFaceData.setMouthBottomPosition(getLandmarkPosition(face, Landmark.LEFT_CHEEK));
        mFaceData.setMouthBottomPosition(getLandmarkPosition(face, Landmark.RIGHT_CHEEK));
        mFaceData.setNoseBasePosition(getLandmarkPosition(face, Landmark.NOSE_BASE));
        mFaceData.setMouthBottomPosition(getLandmarkPosition(face, Landmark.LEFT_EAR));
        mFaceData.setMouthBottomPosition(getLandmarkPosition(face, Landmark.LEFT_EAR_TIP));
        mFaceData.setMouthBottomPosition(getLandmarkPosition(face, Landmark.RIGHT_EAR));
        mFaceData.setMouthBottomPosition(getLandmarkPosition(face, Landmark.RIGHT_EAR_TIP));
        mFaceData.setMouthLeftPosition(getLandmarkPosition(face, Landmark.LEFT_MOUTH));
        mFaceData.setMouthBottomPosition(getLandmarkPosition(face, Landmark.BOTTOM_MOUTH));
        mFaceData.setMouthRightPosition(getLandmarkPosition(face, Landmark.RIGHT_MOUTH));

        // 1
        final float EYE_CLOSED_THRESHOLD = 0.4f;
        float leftOpenScore = face.getIsLeftEyeOpenProbability();
        if (leftOpenScore == Face.UNCOMPUTED_PROBABILITY) {
            mFaceData.setLeftEyeOpen(mPreviousIsLeftEyeOpen);
        } else {
            mFaceData.setLeftEyeOpen(leftOpenScore > EYE_CLOSED_THRESHOLD);
            mPreviousIsLeftEyeOpen = mFaceData.isLeftEyeOpen();
        }
        float rightOpenScore = face.getIsRightEyeOpenProbability();
        if (rightOpenScore == Face.UNCOMPUTED_PROBABILITY) {
            mFaceData.setRightEyeOpen(mPreviousIsRightEyeOpen);
        } else {
            mFaceData.setRightEyeOpen(rightOpenScore > EYE_CLOSED_THRESHOLD);
            mPreviousIsRightEyeOpen = mFaceData.isRightEyeOpen();
        }

        // 2
        // See if there's a smile!
        // Determine if person is smiling.
        final float SMILING_THRESHOLD = 0.8f;
        mFaceData.setSmiling(face.getIsSmilingProbability() > SMILING_THRESHOLD);

        mFaceGraphic.update(mFaceData);
    }

    // 3
    @Override
    public void onMissing(FaceDetector.Detections<Face> detectionResults) {
        mOverlay.remove(mFaceGraphic);
    }

    @Override
    public void onDone() {
        mOverlay.remove(mFaceGraphic);
    }


    // Facial landmark utility methods
    // ===============================

    /** Given a face and a facial landmark position,
     *  return the coordinates of the landmark if known,
     *  or approximated coordinates (based on prior data) if not.
     */
    private PointF getLandmarkPosition(Face face, int landmarkId) {
        for (Landmark landmark : face.getLandmarks()) {
            if (landmark.getType() == landmarkId) {
                return landmark.getPosition();
            }
        }

        PointF landmarkPosition = mPreviousLandmarkPositions.get(landmarkId);
        if (landmarkPosition == null) {
            return null;
        }

        float x = face.getPosition().x + (landmarkPosition.x * face.getWidth());
        float y = face.getPosition().y + (landmarkPosition.y * face.getHeight());
        return new PointF(x, y);
    }

    private void updatePreviousLandmarkPositions(Face face) {
        for (Landmark landmark : face.getLandmarks()) {
            PointF position = landmark.getPosition();
            float xProp = (position.x - face.getPosition().x) / face.getWidth();
            float yProp = (position.y - face.getPosition().y) / face.getHeight();
            mPreviousLandmarkPositions.put(landmark.getType(), new PointF(xProp, yProp));
        }
    }
}
