# Edytor wielokątów — punkty 1–9

Aplikacja desktopowa w Kotlinie i Compose Multiplatform. Scena początkowa zawiera trzy wielokąty, w tym jeden samoprzecinający się, przykład segmentu Béziera i łuk okręgu. Można wybrać dowolny z nich, przeciągać całe wielokąty i wierzchołki oraz wstawiać i usuwać wierzchołki. Edytor obsługuje też zadaną długość krawędzi oraz prostopadłość dwóch krawędzi, także z różnych wielokątów. Scenę edytuje się w `DemoScene.kt`.

## Uruchomienie

Wymagany jest JDK 21. Ustaw `JAVA_HOME` na katalog tej wersji JDK, a następnie na macOS lub Windows:

```sh
./gradlew run
```

W Windows można użyć `gradlew.bat run`. Przycisk „Przywróć scenę” odtwarza dane startowe. W tej wersji nie ma zapisu do pliku.

## Obsługa

- „Pomoc · F1” otwiera wbudowaną instrukcję z obsługą myszy i klawiatury, założeniami, modelem danych i algorytmami. Jej źródłem jest [help.md](src/main/resources/help.md), dołączany do aplikacji jako zasób.
- „Nowy wielokąt” rozpoczyna szkic. Klikaj kolejne punkty lewym przyciskiem; kliknięcie pierwszego punktu, Enter lub „Zamknij wielokąt” zamyka figurę. Escape anuluje, Backspace usuwa ostatni punkt. Potrzeba co najmniej trzech wierzchołków. Samoprzecięcia są dozwolone.
- Przeciąganie działa lewym przyciskiem. Edycja używa bezwzględnej pozycji kursora z zachowaniem początkowego odstępu od środka punktu; odmowa jednego ruchu nie rozstraja kolejnych ruchów.
- Escape przerywa bieżącą czynność i czyści wybór, Delete usuwa wybrany wierzchołek. Podczas wpisywania długości skróty edytora nie przechwytują edycji tekstu.
- Na górze panelu wybierz radio button „Biblioteczny” albo „Bresenham”. Zmiana działa od razu i dotyczy odcinków, ich zaznaczenia oraz łączenia kolejnych punktów Béziera.
- Kliknij wierzchołek i przeciągnij go, aby zmienić kształt.
- Kliknij krawędź albo wnętrze i przeciągnij, aby przesunąć wielokąt.
- Po wybraniu krawędzi użyj „Dodaj wierzchołek”, aby podzielić ją w połowie.
- Po wybraniu wierzchołka użyj „Usuń wierzchołek”. Wielokąt musi mieć co najmniej trzy wierzchołki.
- Kliknij jedną krawędź, aby ustawić jej zadaną długość. Okno dialogowe pokazuje aktualną długość jako wartość domyślną.
- Kliknij dwie krawędzie, aby ustawić między nimi prostopadłość. Kliknięcie już zaznaczonej krawędzi usuwa ją z wyboru.
- Obie krawędzie prostopadłej pary można edytować: podczas przeciągania krawędź związana z przesuwanym wierzchołkiem steruje kierunkiem, a druga się dopasowuje. W każdej połączonej grupie relacji wybieramy jedno źródło ruchu; jeśli edycja dotyczy kilku jej krawędzi, wybieramy tę o najmniejszym ID. Priorytet jest przekazywany przez łańcuch relacji. Korekty uwzględniają pozycję przeciąganego wierzchołka i wspólne punkty innych krawędzi, aby kolejne relacje nie przestawiały sobie punktów odniesienia. Kolejność kliknięcia pary nie ustala ról na stałe. Pełny solver nadal jest iteracyjny i może odrzucić trudny układ relacji.
- Ograniczenia można usuwać z listy w lewym panelu. Podział krawędzi lub usunięcie sąsiedniego wierzchołka usuwa relacje tej krawędzi.
- Po wybraniu krawędzi użyj „Zmień na Béziera”, aby utworzyć krzywą trzeciego stopnia. Przycisk ponownie zmienia ją w odcinek.
- Punkty kontrolne krzywej są pomarańczowymi uchwytami połączonymi z jej końcami linią przerywaną; przeciągnij uchwyt, aby zmienić przebieg krzywej. Przy G1 lub C1 sąsiedni uchwyt albo drugi koniec sąsiedniego odcinka przesunie się automatycznie.
- Pozycja przeciąganego uchwytu jest sprawdzana przy zatwierdzeniu operacji. Samo G1/C1 nie przesuwa końców jego segmentu; zadana długość krzywej może tego wymagać — wtedy skalujemy pozostałą geometrię wokół przeciąganego uchwytu. Jeśli solver nie uzyska poprawnego wyniku w pozycji kursora, odrzuca operację z komunikatem.
- Po wybraniu wierzchołka sąsiadującego z krzywą wybierz G0, G1 albo C1. Nowo utworzona krzywa dostaje domyślnie C1, dopóki nie wybierzesz klasy ręcznie.
- Po wybraniu krawędzi użyj „Zmień na łuk”. Przycisk „Łuk → odcinek” przywraca odcinek. Można też przełączyć łuk bezpośrednio w Béziera i odwrotnie.
- Łuk ma G0 albo G1 na końcach. C1 nie jest dostępne przy łuku. Przy G0 na obu końcach powstaje półokrąg o promieniu równym połowie odległości końców krawędzi (długości cięciwy).
- G1 może być ustawione tylko na jednym końcu danego łuku. Aby przenieść G1 na drugi koniec, najpierw wybierz G0 na pierwszym. Promień i strona łuku G1 wynikają ze stycznej sąsiedniej krawędzi. Przesunięcie sąsiedniego wierzchołka lub uchwytu Béziera przelicza łuk.
- Przełączenie krawędzi z C1 w łuk ustawia w tych wierzchołkach G0. Jeżeli oba końce mają G1, przełączenie jest odrzucane; najpierw należy zmienić jeden koniec na G0. Zerowa cięciwa lub styczna równoległa do cięciwy przy G1 nie wyznaczają skończonego łuku i powodują odrzucenie operacji.
- Długość łuku okręgu to dokładne `promień × |kąt środkowy w radianach|`. Prostopadłość dotyczy cięciw krawędzi. Dla Béziera długość jest przybliżana numerycznie.
- Podział łuku wstawia wierzchołek w połowie jego przebiegu. Jeśli oba zewnętrzne końce mają G0, nowy wierzchołek otrzymuje G1 i obie części zachowują kształt. Jeśli zewnętrzny koniec ma już G1, nowy wierzchołek otrzymuje G0, a promienie części są przeliczane zgodnie z wymaganiami (część G0–G0 staje się półokręgiem). Zmiany topologii ponownie sprawdzają relacje; niemożliwa operacja jest odrzucana.

## Geometria łuków

`CircularArc.kt` zawiera matematykę okręgu: wyliczanie środka, promienia, punktu na łuku i stycznych. `EdgeShape.CircularArc` przechowuje kąt środkowy, a końce nadal należą do wierzchołków wielokąta. Dzięki temu przesuwanie końców nie odrywa ich od łuku.

Dla G0–G0 kąt wynosi π. Dla G1 rozwiązujemy okrąg przechodzący przez oba końce i mający wskazaną styczną. Kierunek stycznej może wymagać łuku większego niż półokrąg. Model sprawdza, czy na obu końcach jednego łuku nie ustawiono G1. Interfejs również blokuje drugi wybór G1.

## Algorytmy rysowania — punkt 5

`drawing/DrawingAlgorithms.kt` zawiera algorytmy niezależne od Compose. `drawing/SceneRenderer.kt` przelicza scenę na ekran i rysuje wynik, a `Main.kt` przechowuje wybór radio buttonów. Algorytm rysowania nie zmienia wierzchołków ani ograniczeń sceny.

### Odcinki

Tryb „Biblioteczny” używa `drawLine`. Tryb „Bresenham” sam wyznacza całkowitoliczbowe współrzędne pikseli. Oblicza odległości `dx`, `dy`, kierunki kroków oraz błąd, a następnie w pętli wybiera krok w osi X, Y albo w obu osiach. Działa dla każdego nachylenia, w obu kierunkach, także dla odcinka zerowej długości. Akumulator błędu używa `Long`, aby różnica współrzędnych `Int` nie przepełniała obliczeń.

Najpierw przeliczamy współrzędne sceny na współrzędne ekranu. Piksele wybrane przez Bresenhama są nanoszone jako kwadratowe punkty przez `drawPoints`; biblioteka nie wybiera za nas pikseli odcinka. Dla grubszego obrysu te same punkty są nanoszone kwadratowym stemplem o nieparzystej liczbie pikseli boku. Centra `x + 0.5`, `y + 0.5` wyrównują stemple do granic pikseli. Nie skalujemy już gotowego rastra.

Odcinki wychodzące poza obszar rysowania przycinamy algorytmem Lianga–Barsky’ego przed rasteryzacją. Dzięki temu długa niewidoczna część krawędzi nie powoduje ogromnej liczby iteracji. Przy przecięciu z granicą okna raster zaczyna się od zaokrąglonego punktu przecięcia.

### Bézier: baza potęgowa i różnice skończone

Krzywą sześcienną z punktami `P0`, `P1`, `P2`, `P3` przepisujemy z postaci Bernsteina do postaci:

```text
B(t) = a*t³ + b*t² + c*t + d
a = -P0 + 3*P1 - 3*P2 + P3
b = 3*P0 - 6*P1 + 3*P2
c = -3*P0 + 3*P1
d = P0
```

W kodzie liczymy współczynniki z różnic punktów, więc `a`, `b`, `c` są wektorami, a `d` punktem. Nie dodajemy do siebie położeń jak wektorów.

Dla stałego kroku parametru `h = 1/N` obliczamy początkowe różnice:

```text
ΔB   = a*h³ + b*h² + c*h
Δ²B  = 6*a*h³ + 2*b*h²
Δ³B  = 6*a*h³
```

Następnie każda iteracja wykonuje tylko dodawanie:

```text
punkt = punkt + ΔB
ΔB    = ΔB + Δ²B
Δ²B   = Δ²B + Δ³B
```

W każdej iteracji ważna jest kolejność: najpierw aktualizujemy punkt ze starej pierwszej różnicy, potem pierwszą różnicę ze starej drugiej. Trzecia różnica jest stała. Ostatnią próbkę ustawiamy dokładnie na `P3`, żeby skumulowane zaokrąglenia nie powodowały szczeliny w połączeniu.

Liczba kroków zależy od punktów kontrolnych po przeliczeniu na ekran. Korzystamy z ograniczenia prędkości `|B'(t)| <= 3 * max(|P1-P0|, |P2-P1|, |P3-P2|)`. Wybieramy `N` tak, aby odstęp próbek wynosił najwyżej około jednego piksela, z limitem 100 000 kroków na segment. Limit chroni płynność dla skrajnie odległych punktów kontrolnych; po jego osiągnięciu odstępy mogą być większe.

Te same próbki są używane do wypełnienia, obrysu i zaznaczenia. Próbki łączymy biblioteczną łamaną albo Bresenhamem, zgodnie z radio buttonem. Renderowanie Béziera nie wywołuje `cubicTo` ani nie oblicza wielomianu osobno w każdej iteracji. Matematyczny `pointAt` w modelu nadal służy m.in. do podziału krzywej, obliczeń długości i wykrywania kliknięć.

Łuki okręgów korzystają z bibliotecznego `arcTo`. Wypełnienie, przerywane linie pomocnicze oraz uchwyty również rysuje biblioteka. Wypełnienie i wykrywanie wnętrza korzystają z niezerowej liczby nawinięć (non-zero winding). Dzięki temu środek samoprzecinającej się gwiazdy jest wypełniony i można go kliknąć, aby przeciągać wielokąt.

## Testy i pakowanie

```sh
./gradlew test
./gradlew packageDistributionForCurrentOS
```

Testy obejmują wszystkie oktanty Bresenhama, porównanie różnic skończonych z niezależnym wzorem Bernsteina i rzeczywisty odczyt pikseli z Canvas. Test renderera zapisuje obrazy obu trybów w `build/reports/rendering/` do porównania wyglądu.

Pakiet dla każdego systemu trzeba budować na tym systemie.

## Scena początkowa i architektura — punkty 7–9

Scena z `DemoScene.kt` zawiera gwiazdę, pięciokąt z Bézierem i czworokąt z łukiem. Górna krawędź czworokąta ma zadaną długość 160 i jest prostopadła do prawej. Obie relacje są spełnione już w danych startowych. Dopisanie kolejnego wywołania pomocniczej funkcji `polygon` dodaje figurę bez ręcznego przydzielania ID. Parametr `demoScene(withConstraints = false)` służy testom niezależnych operacji.

Model i solver w `model/` nie zależą od Compose. Punkty i wektory są w `Geometry.kt`, krawędzie w `Edge.kt` i `EdgeGeometry.kt`, operacje sceny w `Scene.kt`, dane relacji w `Constraints.kt`, a ich rozwiązywanie w `ConstraintSolver.kt`. `HitTesting.kt` zawiera wykrywanie trafień, a `BezierEditing.kt` podział krzywej. `drawing/` oddziela czyste algorytmy od ich rysowania, `interaction/DragGesture.kt` obsługuje położenie przeciąganego obiektu względem kursora, `Main.kt` przechowuje stan interfejsu, a `ui/HelpDialog.kt` udostępnia dokumentację. Szczegółowe opracowanie do obrony jest w [PRZYGOTOWANIE_DO_ODDANIA.md](docs/PRZYGOTOWANIE_DO_ODDANIA.md).

Jeśli katalog projektu jest obsługiwany przez macOS File Provider i `jpackage` zgłosi błąd `Disallowed xattr com.apple.FinderInfo`, wskaż zwykły katalog tymczasowy na pakiet: `./gradlew packageDmg -PpackageOutputDir=/private/tmp/polygon-editor-package`. Zbudowany pakiet znajdzie się wtedy w podanym katalogu.
