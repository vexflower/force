package entity;

import environment.RendererManager;
import lang.GeomMath;
import lang.Mat4;
import model.Material;
import model.Mesh;
import move.Move;
import move.MoveType;

public class Entity {
    public final int id;

    public float x, y, z;
    public float rotX, rotY, rotZ;
    public float scale = 1.0f;

    public Material material = new Material();
    public final Mat4 modelMatrix = new Mat4();
    public boolean isDirty = true;

    // --- ZERO-GC ANIMATION TRACKERS ---
    public final Move posMove = new Move();
    public final Move rotMove = new Move();
    public final Move scaleMove = new Move();


    public Entity(Mesh mesh, int textureId) {
        this.id = RendererManager.createEntity();
        RendererManager.meshIds.set(id, mesh.vaoId);
        RendererManager.diffuseTextureIds.set(id, textureId);
        this.material.diffuseTextureId = textureId;
    }

    public void setPosition(float x, float y, float z) {
        this.x = x; this.y = y; this.z = z;
        isDirty = true;
    }
    public void setRotation(float rx, float ry, float rz) { this.rotX = rx; this.rotY = ry; this.rotZ = rz;
        isDirty = true; }
    public void setScale(float s) { this.scale = s;
        isDirty = true; }

    // --- ANIMATION API ---

    public void move(int moveTypeCurve, int moveType, float destX, float destY, float destZ, float duration) {
        float ex = moveType == MoveType.STUDS ? x + destX : destX;
        float ey = moveType == MoveType.STUDS ? y + destY : destY;
        float ez = moveType == MoveType.STUDS ? z + destZ : destZ;
        posMove.start(moveTypeCurve, x, y, z, ex, ey, ez, duration);
    }

    public void moveRotate(int moveTypeCurve, int moveType, float destX, float destY, float destZ, float duration) {
        float ex = moveType == MoveType.STUDS ? rotX + destX : destX;
        float ey = moveType == MoveType.STUDS ? rotY + destY : destY;
        float ez = moveType == MoveType.STUDS ? rotZ + destZ : destZ;
        rotMove.start(moveTypeCurve, rotX, rotY, rotZ, ex, ey, ez, duration);
    }

    public void moveScale(int moveTypeCurve, int moveType, float destScale, float duration) {
        float ex = moveType == MoveType.STUDS ? scale + destScale : destScale;
        scaleMove.start(moveTypeCurve, scale, 0, 0, ex, 0, 0, duration);
    }

    // Backwards compatibility for your current Main.java loop
    public void moveRotate(float rx, float ry, float rz, float duration) {
        moveRotate(Move.NORMAL, MoveType.STUDS, rx, ry, rz, duration);
    }

    public void update(float delta) {
        // Update Position
        if (posMove.active) {
            posMove.time += delta;
            float t = Math.min(posMove.time / posMove.duration, 1.0f);
            float eased = posMove.getEased(t);
            this.x = posMove.startX + (posMove.endX - posMove.startX) * eased;
            this.y = posMove.startY + (posMove.endY - posMove.startY) * eased;
            this.z = posMove.startZ + (posMove.endZ - posMove.startZ) * eased;
            isDirty = true;
            if (t >= 1.0f) posMove.active = false;
        }

        // Update Rotation
        if (rotMove.active) {
            rotMove.time += delta;
            float t = Math.min(rotMove.time / rotMove.duration, 1.0f);
            float eased = rotMove.getEased(t);
            this.rotX = rotMove.startX + (rotMove.endX - rotMove.startX) * eased;
            this.rotY = rotMove.startY + (rotMove.endY - rotMove.startY) * eased;
            this.rotZ = rotMove.startZ + (rotMove.endZ - rotMove.startZ) * eased;
            isDirty = true;
            if (t >= 1.0f) rotMove.active = false;
        }

        // Update Scale
        if (scaleMove.active) {
            scaleMove.time += delta;
            float t = Math.min(scaleMove.time / scaleMove.duration, 1.0f);
            float eased = scaleMove.getEased(t);
            this.scale = scaleMove.startX + (scaleMove.endX - scaleMove.startX) * eased;
            isDirty = true;
            if (t >= 1.0f) scaleMove.active = false;
        }

        if (isDirty) {
            GeomMath.createTransformationMatrix(x, y, z, rotX, rotY, rotZ, scale, scale, scale, modelMatrix);
            isDirty = false;
        }
    }
}