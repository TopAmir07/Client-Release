package dev.topclient.client.clover.module;

/** Extra field on the game's dropped-item render state: the Item Physics pose (null = normal). */
public interface ItemPose {
	float[] topclient$pose();

	void topclient$setPose(float[] pose);
}
