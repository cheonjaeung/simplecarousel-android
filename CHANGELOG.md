# Changelog

## 0.3.0

_2024.10.06_

### Changed

- `CarouselSnapHelper` is added to support snapping of `CarouselLayoutManager`.
- `CarouselLayoutManager` now has functions to get visible item (`findFirstVisibleItemPosition`, `findFirstCompletelyVisibleItemPosition`, `findLastVisibleItemPosition`, `findLastCompletelyVisibleItemPosition`).
- `ViewBoundsHelper` is added to check visibility of child view within parent view.

## 0.2.2

_2024.10.04_

### Fixed

- Recycling was not working when `RecyclerView` had padding bigger than 0.
- `reverseLayout` was not applied to scroll direction in `CarouselLayoutManager.computeScrollVectorForPosition`.

## 0.2.1

_2024.09.28_

### Fixed

- Scrolling was stuck when `RecyclerView` had padding bigger than 0.

## 0.2.0

_2024.09.17_

### Changed

- `CarouselLayoutManager` is now inheritable.
- Add support for RTL.
- Add `reverseLayout`. It can be set from XML with `reverseLayout` in `RecyclerView` or `reverseLayout` property in `CarouselLayoutManager`.
- Add support for `clipToPadding` of `RecyclerView`.

## 0.1.0

_2024.08.31_

Initial release.
