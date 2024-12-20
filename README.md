# SimpleCarousel for Android

SimpleCarousel is an Android library contains components for carousel UI.

## Installation

Add following dependency to download this library:

```kotlin
dependencies {
    implementation("com.cheonjaeung.simplecarousel.android:simplecarousel:<version>")
}
```

## Getting Started

You can just set the `CarouselLayoutManager` to make your `RecyclerView` work as a carousel.
It can be set programmatically or as a XML attribute.

```xml
<androidx.recyclerview.widget.RecyclerView
    android:id="@+id/recyclerView"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:layoutManager="com.cheonjaeung.simplecarousel.android.CarouselLayoutManager" />
```

```kotlin
val recyclerView = findViewById<RecyclerView>(R.id.recyclerView)
recyclerView.layoutManager = CarouselLayoutManager()
```

There is `circular` mode in the `CarouselLayoutManager`, enabled as default.
When it is enabled, the layout manager places first/last item next to last/first item.
You can set `circular` programmatically.

```kotlin
val layoutManager = CarouselLayoutManager()
layoutManager.circular = true|false
```

## License

SimpleCarousel is licensed under the Apache License 2.0.

```
Copyright 2024 Cheon Jaeung

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
