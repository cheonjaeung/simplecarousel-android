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

Copyright 2024 Cheon Jaeung.

SimpleCarousel is licensed under the Apache License 2.0. See [license](./LICENSE.txt) for more details.
