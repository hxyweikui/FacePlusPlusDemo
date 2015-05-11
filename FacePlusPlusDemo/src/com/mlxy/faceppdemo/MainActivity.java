package com.mlxy.faceppdemo;

import java.io.ByteArrayOutputStream;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;

import com.facepp.error.FaceppParseException;
import com.facepp.http.HttpRequests;
import com.facepp.http.PostParameters;

public class MainActivity extends Activity {
	private ImageView imageViewPreview;
	private EditText editTextPath;
	private Button buttonSelectImage;

	private Bitmap selectedImage;

	private HttpRequests req;
	
	private AlertDialog dialogWaiting;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		req = new HttpRequests(Constants.API_KEY, Constants.API_SECRET);
		
		imageViewPreview = (ImageView) findViewById(R.id.imageView1);
		
		findViewById(R.id.button1).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
				startActivityForResult(intent, 1);
			}
		});
		
		editTextPath = (EditText) findViewById(R.id.editText1);
		buttonSelectImage = (Button) findViewById(R.id.button2);
		buttonSelectImage.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (selectedImage != null) {
					// 清除状态。
					selectedImage = null;
					buttonSelectImage.setText("选择");
					editTextPath.setText("");
					imageViewPreview.setImageBitmap(null);
				} else {
					Intent intent = new Intent("android.intent.action.PICK",
	                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
					startActivityForResult(intent, 2);
				}
			}
		});
		
		dialogWaiting = new AlertDialog.Builder(this)
										.setView(new ProgressBar(this))
										.setCancelable(false)
										.create();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		
		if (requestCode == 1 && resultCode == RESULT_OK && data != null) {
			// 拍照识别。
			Bitmap bitmap = (Bitmap) data.getExtras().get("data");
			
			if (bitmap != null) {
				imageViewPreview.setImageBitmap(bitmap);
				
				dialogWaiting.show();
				sendRequest(bitmap2Bytes(bitmap));
			}
		} else if (requestCode == 2 && resultCode == RESULT_OK && data != null) {
			// 选择照片。
			String path = FileUtils.getPath(this, data.getData());
			selectedImage = BitmapFactory.decodeFile(path);
			Matrix matrix = new Matrix();
			matrix.postRotate(90);
			// 把图片旋转回正确的角度。
			selectedImage = Bitmap.createBitmap(selectedImage, 0, 0,
					selectedImage.getWidth(), selectedImage.getHeight(), matrix, true);

			buttonSelectImage.setText("清除");
			editTextPath.setText(path);
			
			imageViewPreview.setImageBitmap(selectedImage);
		}
	}
	
	public byte[] bitmap2Bytes(Bitmap bm) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		bm.compress(Bitmap.CompressFormat.PNG, 100, baos);
		return baos.toByteArray();
	}
	
	/** 发送请求。*/
	private void sendRequest(byte[] imageBytes) {
		PostParameters param = new PostParameters();
		param.setImg(imageBytes);
		param.setAttribute("glass,pose,gender,age,race,smiling");
		
		if (selectedImage == null) {
			detect(param);
		} else {
			verify(param);
		}
	}

	/** 提交人脸识别请求。*/
	private void detect(final PostParameters param) {
		new Thread(new Runnable() {
			public void run() {
				try {
					final JSONObject result = req.detectionDetect(param);
					dialogWaiting.dismiss();
					if (result != null) {
						Log.v("asdf", result.toString());
						showInfoDialog(result);
					} else {
						Log.v("asdf", "null");
					}
				} catch (FaceppParseException e) {
					e.printStackTrace();
				}
			}
		}).start();
	}
	
	/** 显示人脸识别结果对话框。*/
	private void showInfoDialog(JSONObject info) {
		JSONArray face;
		try {
			face = info.getJSONArray("face");
			
			if (face.length() != 0) {
				JSONObject attribute = face.getJSONObject(0).getJSONObject("attribute");
				
				int age = attribute.getJSONObject("age").getInt("value");
				int ageRange = attribute.getJSONObject("age").getInt("range");
				
				String gender = attribute.getJSONObject("gender").getString("value");
				double genderConfidence = attribute.getJSONObject("gender").getDouble("confidence");
				
				String glass = attribute.getJSONObject("glass").getString("value");
				double glassConfidence = attribute.getJSONObject("glass").getDouble("confidence");
				
				String race = attribute.getJSONObject("race").getString("value");
				double raceConfidence = attribute.getJSONObject("race").getDouble("confidence");
				
				double smiling = attribute.getJSONObject("smiling").getDouble("value");
				
				final String conclusion = String.format("年龄：%s，误差：%s\n"
						+ "性别：%s，可信度：%s\n"
						+ "眼镜：%s，可信度：%s\n"
						+ "种族：%s，可信度：%s\n"
						+ "笑容：%s",
						age, ageRange,
						gender, genderConfidence,
						glass, glassConfidence,
						race, raceConfidence,
						smiling);
				
				runOnUiThread(new Runnable() {
					public void run() {
						new AlertDialog.Builder(MainActivity.this)
						.setMessage(conclusion)
						.setPositiveButton("确定", null)
						.show();
					}
				});
			} else {
				runOnUiThread(new Runnable() {
					public void run() {
						new AlertDialog.Builder(MainActivity.this)
						.setMessage("未识别到人脸")
						.setPositiveButton("确定", null)
						.show();
					}
				});
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}
	
	/** 提交验脸请求。*/
	private void verify(final PostParameters param) {
		new Thread(new Runnable() {
			public void run() {
				try {
					JSONObject result1 = req.detectionDetect(param);
					PostParameters param2 = new PostParameters();
					param2.setAttribute("glass,pose,gender,age,race,smiling");
					param2.setImg(bitmap2Bytes(selectedImage));
					JSONObject result2 = req.detectionDetect(param2);
					Log.v("asdf", "result1:" + result1.toString());
					Log.v("asdf", "result2:" + result2.toString());
					dialogWaiting.dismiss();
					
					if (result1 != null && result2 != null &&
							result1.getJSONArray("face").length() != 0 && result2.getJSONArray("face").length() != 0) {
						compare(result1.getJSONArray("face"), result2.getJSONArray("face"));
					} else {
						runOnUiThread(new Runnable() {
							public void run() {
								new AlertDialog.Builder(MainActivity.this)
								.setMessage("未识别到人脸")
								.setPositiveButton("确定", null)
								.show();
							}
						});
					}
				} catch (FaceppParseException e) {
					e.printStackTrace();

					dialogWaiting.dismiss();
					runOnUiThread(new Runnable() {
						public void run() {
							new AlertDialog.Builder(MainActivity.this)
							.setMessage("您的颜值太高，服务器不能承受了！")
							.setPositiveButton("确定", null)
							.show();
						}
					});
				} catch (JSONException e) {
					e.printStackTrace();
				}
			}
		}).start();
	}
	
	/** 比较两张脸。*/
	private void compare(JSONArray face1, JSONArray face2) {
		try {
			String faceId1 = face1.getJSONObject(0).getString("face_id");
			String faceId2 = face2.getJSONObject(0).getString("face_id");
			
			PostParameters param = new PostParameters();
			param.setFaceId1(faceId1).setFaceId2(faceId2);
			JSONObject result = req.recognitionCompare(param);
			
			if (result != null) {
				Log.v("asdf", result.toString());
				String similarity = result.getString("similarity");
				
				JSONObject componentSimilarity = result.getJSONObject("component_similarity");
				double eye = componentSimilarity.getDouble("eye");
				double mouth = componentSimilarity.getDouble("mouth");
				double nose = componentSimilarity.getDouble("nose");
				double eyebrow = componentSimilarity.getDouble("eyebrow");
				
				final String conclusion = String.format("相似度：%s\n"
												+ "眉毛：%s\n眼睛：%s\n鼻子：%s\n嘴：%s",
												similarity, eyebrow, eye, nose, mouth);
				
				runOnUiThread(new Runnable() {
					public void run() {
						new AlertDialog.Builder(MainActivity.this)
						.setMessage(conclusion)
						.setPositiveButton("确定", null)
						.show();
					}
				});
			}
		} catch (JSONException e) {
			e.printStackTrace();
		} catch (FaceppParseException e) {
			e.printStackTrace();
		}
		
	}
}
