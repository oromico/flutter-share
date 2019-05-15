# Share Anything plugin

Original: [d-silveira/flutter-share](https://github.com/d-silveira/flutter-share)

A Flutter plugin to share content from your Flutter app via the platform's share dialog and receive shares from other apps on the platform (currently only on Android).

## Usage

### Setup

1. add `share`
```
   share:
      git:
         url: https://github.com/seiyial/flutter-share.git
```
 as a [dependency in your pubspec.yaml file](https://flutter.io/platform-plugins/).

### Sharing Outbound from within your App

1. Put this in `main.dart` or whichever file you are performing the outbound share:-

```
import 'package:share/share.dart';
```

2. **Create an instance** of a `Share` using one of the following.

```dart
Share.plainText(text: <String>, title: <String>);
Share.file(path: <String>, mimeType: ShareType, title: , text: );
Share.image(path: , mimeType: , title: , text: );
Share.multiple(shares: List<Share>, mimeType: , title: );
```

Note: for each of the above methods, only the first argument is required.

3. Call `.share({ Rect sharePositionOrigin })` on the instance. Example:-

```dart
Share _fileToShare = Share.plainText(text: 'Somebodys@email.com');

final RenderBox _box = context.findRenderObject();
_fileToShare.share(sharePositionOrigin: _box.localToGlobal(Offset.zero) & _box.size);

// Another example
final RenderBox _box2 = context.findRenderObject();
Share.image(
   path: "content://0@media/external/images/media/2129",
   mimeType: ShareType.TYPE_IMAGE
).share(
   sharePositionOrigin: _box2.localToGlobal(Offset.zero) & _box2.size
);
```

### Receiving an Inbound share from another App

1. In your Android `MainActivity`, replace `extends FlutterActivity` with `extends FlutterShareReceiverActivity`.

**MainActivity.java**

```java
// ...
import io.flutter.plugins.share.FlutterShareReceiverActivity;
// ...

// ...
// Originally FlutterActivity
public class MainActivity extends FlutterShareReceiverActivity {
   // ...
}
// ...
```

2. Your `main.dart` should receive a share.

```dart
import 'package:share/receive_share_state.dart';
```

3. In your StatefulWidget replace your `extends State<T>` with `extends ReceiveShareState<T>` and implement your mandatory `@override void receiveShare(Share shareObj) { }` where you'll receive your shares.

finally call ``enableShareReceiving();`` in your initState().

That's it!

## Example

Check out the example in the example project folder for a working example.

## Notes

Currently only the Android part is complete (IOS part does the same as google's original version), but be on the lookout for new versions, as the IOS part is being worked on and will soon do all the same bells and whistles.

- [] Add instruction to put `android.intent.action.VIEW` (for viewing received files) and `android.intent.action.SEND` in the AndroidManifest
