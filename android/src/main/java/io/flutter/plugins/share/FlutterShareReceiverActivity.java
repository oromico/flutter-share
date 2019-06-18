package io.flutter.plugins.share;

import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import io.flutter.app.FlutterActivity;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;

import static io.flutter.plugins.share.SharePlugin.IS_MULTIPLE;
import static io.flutter.plugins.share.SharePlugin.PATH;
import static io.flutter.plugins.share.SharePlugin.FILEPATH;
import static io.flutter.plugins.share.SharePlugin.TEXT;
import static io.flutter.plugins.share.SharePlugin.TITLE;
import static io.flutter.plugins.share.SharePlugin.TYPE;

/**
 * main activity super, handles eventChannel sink creation , share intent
 * parsing and redirecting to eventChannel sink stream
 *
 * @author Duarte Silveira
 * @version 1
 * @since 25/05/18
 */
public class FlutterShareReceiverActivity extends FlutterActivity {

	public static final String STREAM = "plugins.flutter.io/receiveshare";

	private EventChannel.EventSink eventSink = null;
	private boolean inited = false;
	private List<Intent> backlog = new ArrayList<>();
	private boolean ignoring = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		if (!inited) {
			init(getFlutterView(), this);
		}
	}

	public void init(BinaryMessenger flutterView, Context context) {
		Log.i(getClass().getSimpleName(), "initializing eventChannel");

		context.startActivity(new Intent(context, ShareReceiverActivityWorker.class));

		// Handle other intents, such as being started from the home screen
		new EventChannel(flutterView, STREAM).setStreamHandler(new EventChannel.StreamHandler() {
			@Override
			public void onListen(Object args, EventChannel.EventSink events) {
				Log.i(getClass().getSimpleName(), "adding listener");
				eventSink = events;
				ignoring = false;
				for (int i = 0; i < backlog.size(); i++) {
					handleIntent(backlog.remove(i));
				}
			}

			@Override
			public void onCancel(Object args) {
				Log.i(getClass().getSimpleName(), "cancelling listener");
				ignoring = true;
				eventSink = null;
			}
		});

		inited = true;

		handleIntent(getIntent());

	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		handleIntent(intent);
	}

	public void handleIntent(Intent intent) {
		// Get intent, action and MIME type
		String action = intent.getAction();
		String type = intent.getType();

		if (Intent.ACTION_SEND.equals(action) && type != null) {

			// Handler for text/plain types
			if ("text/plain".equals(type)) {
				String sharedTitle = intent.getStringExtra(Intent.EXTRA_SUBJECT);
				Log.i(getClass().getSimpleName(), "receiving shared title: " + sharedTitle);
				String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
				Log.i(getClass().getSimpleName(), "receiving shared text: " + sharedText);
				if (eventSink != null) {
					Map<String, String> params = new HashMap<>();
					params.put(TYPE, type);
					params.put(TEXT, sharedText);
					if (!TextUtils.isEmpty(sharedTitle)) {
						params.put(TITLE, sharedTitle);
					}
					eventSink.success(params);
				} else if (!ignoring && !backlog.contains(intent)) {
					backlog.add(intent);
				}

				// Handler for all other types
			} else {
				// URI
				Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
				if (uri == null) {
					Log.e(getClass().getSimpleName(), "@-> !!! URI is NULL");
					return;
				}
				Log.i(getClass().getSimpleName(), "@-> uri: "+ uri.toString());

				Cursor cursor = getContentResolver().query(uri, null, null, null, null);
				int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
				Log.i(getClass().getSimpleName(), "@-> nameindex: " + String.valueOf(nameIndex));
				cursor.moveToFirst();

				// Title
				// String sharedTitle = intent.getStringExtra(Intent.EXTRA_SUBJECT);
				// if (sharedTitle == null) sharedTitle = intent.getStringExtra(Intent.EXTRA_TITLE);
				// if (sharedTitle == null) sharedTitle = intent.getStringExtra(Intent.EXTRA_TEXT);
				// if (sharedTitle == null) {
				// 	sharedTitle = uri.toString().substring(uri.toString().lastIndexOf("%2F")).replaceAll("\\\\/", "-").replace("%2F", "");
				// }
				String sharedTitle = cursor.getString(nameIndex);
				if (sharedTitle == null) sharedTitle = "Shared.zip";

				// File type
				String mimeType = intent.getType();
				String fileExtension = "zip";
				if (mimeType == "application/pdf") {
					fileExtension = "pdf";
				} else if (mimeType == "*/*") {
					int indexOfDot = sharedTitle.lastIndexOf(".");
					if (indexOfDot > 0) {
						fileExtension = sharedTitle.substring(indexOfDot + 1);
					}
				}

				File cacheDir = getCacheDir();
				String outputFilePath = "";
				Log.i(getClass().getSimpleName(), "@-> Title: "+sharedTitle+", fileExt: "+fileExtension);

				try {
					InputStream inputStream = getContentResolver().openInputStream(uri);

					File outputFile = File.createTempFile(sharedTitle, fileExtension, cacheDir);
					Log.i(getClass().getSimpleName(), "@-> Created files/fileDir objects ");
					outputFilePath = outputFile.getAbsolutePath();
					Log.i(getClass().getSimpleName(), "@-> outputfile is " + outputFile.getAbsolutePath());
					OutputStream outStream = new FileOutputStream(outputFile);
					byte[] buffer = new byte[8 * 1024];
					int bytesRead;

					while ((bytesRead = inputStream.read(buffer)) != -1) {
						outStream.write(buffer, 0, bytesRead);
					}

					Log.i(getClass().getSimpleName(), "@-> Closing Streams ");

					inputStream.close();
					outStream.close();
					Log.i(getClass().getSimpleName(), "@-> Written File! ");

				} catch (Exception e) {
					Log.i(getClass().getSimpleName(), "writing shared file errored" + uri.toString());
				}

				Log.i(getClass().getSimpleName(), "receiving shared title: " + sharedTitle);

				Log.i(getClass().getSimpleName(), "receiving shared file: " + uri.toString());

				if (eventSink != null) {
					Map<String, String> params = new HashMap<>();
					params.put(TYPE, type);
					params.put(PATH, uri.toString());
					params.put(FILEPATH, outputFilePath);
					if (!TextUtils.isEmpty(sharedTitle)) {
						params.put(TITLE, sharedTitle);
					}
					if (!intent.hasExtra(Intent.EXTRA_TEXT)) {
						params.put(TEXT, intent.getStringExtra(Intent.EXTRA_TEXT));
					}
					eventSink.success(params);
				} else if (!ignoring && !backlog.contains(intent)) {
					backlog.add(intent);
				}
			}

		} else if (Intent.ACTION_SEND_MULTIPLE.equals(action) && type != null) {
			Log.i(getClass().getSimpleName(), "receiving shared files!");
			ArrayList<Uri> uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
			if (eventSink != null) {
				Map<String, String> params = new HashMap<>();
				params.put(TYPE, type);
				params.put(IS_MULTIPLE, "true");
				for (int i = 0; i < uris.size(); i++) {
					params.put(Integer.toString(i), uris.get(i).toString());
				}
				eventSink.success(params);
			} else if (!ignoring && !backlog.contains(intent)) {
				backlog.add(intent);
			}

		}
	}
}
