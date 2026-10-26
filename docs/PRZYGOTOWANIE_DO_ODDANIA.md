# Edytor wielokątów — przygotowanie do oddania projektu

Opracowanie opisuje aktualny kod punktów 1–9. Łączy teorię, przykłady liczbowe, decyzje implementacyjne, możliwe pytania prowadzącego i ograniczenia programu. Kolejność odpowiada etapom dodawania funkcjonalności. Późniejsze zmiany, np. wypełnianie non-zero winding i przyrostowe rysowanie Béziera, są opisane jako stan aktualny.

**Jak się uczyć:** w każdym podrozdziale umiej odpowiedzieć: co robi dana operacja, skąd bierze się wzór, co jest jej wejściem i wynikiem, jaki ma przykład i kiedy może się nie udać. Nie ucz się wyłącznie nazw metod.

## Spis treści

1. [Architektura, Kotlin i matematyczne podstawy](#podstawy)
2. [Etap 1: wielokąty, edycja i wybór elementów](#etap-1)
3. [Etap 2: ograniczenia długości i prostopadłości](#etap-2)
4. [Etap 3: sześcienne segmenty Béziera i ciągłość](#etap-3)
5. [Etap 4: łuki okręgów](#etap-4)
6. [Etap 5: Bresenham i przyrostowe rysowanie Béziera](#etap-5)
7. [Etapy 6–9: interakcja, architektura, demonstracja i pomoc](#etapy-6-9)
8. [Testy, złożoność i ograniczenia implementacji](#weryfikacja)
9. [Pytania prowadzącego z odpowiedziami](#pytania)
10. [Scenariusz prezentacji i karta wzorów](#prezentacja)

<a id="podstawy"></a>

## 0. Architektura, Kotlin i matematyczne podstawy

### 0.1. Co to jest za program?

To desktopowy edytor zamkniętych konturów wielokątów, których krawędzie mogą być odcinkami, sześciennymi krzywymi Béziera albo łukami okręgów. Pozwala edytować geometrię i narzucać wybrane relacje. Samoprzecięcia są dozwolone.

Scena początkowa zawiera trzy figury oraz relacje długości i prostopadłości na czworokącie. Użytkownik może tworzyć kolejne wielokąty przez klikanie i zmieniać liczbę ich wierzchołków. Nie ma usuwania całych wielokątów, zapisu do pliku ani undo/redo.

**Przykład:** na scenie wybierasz pięciokąt, zmieniasz krawędź w Béziera, ustawiasz C1 i przeciągasz uchwyt. Kształt zmienia się razem z elementem sąsiednim, tak aby zachować styczną.

### 0.2. Technologie i podział odpowiedzialności

Używamy Kotlina/JVM, Compose Multiplatform Desktop i Gradle. Konfiguracja projektu ma Kotlin 2.2.20, Compose 1.9.3 i toolchain JDK 21. To projekt JVM: kod jest uruchamiany przez JVM; nie jest kompilowany przez Kotlin/Native. Compose Desktop pozwala używać wspólnego kodu aplikacji na macOS i Windows, z odpowiednimi zależnościami dla systemu.

| Plik | Odpowiedzialność | Przykład |
|---|---|---|
| `Main.kt` | Interfejs, zaznaczenie, obsługa myszy i dialogów | Kliknięcie G1 wywołuje operację modelu i pokazuje jej wynik |
| `model/Geometry.kt` | Punkt, wektor i ich operacje | Różnica dwóch punktów daje wektor |
| `model/Edge.kt`, `model/EdgeGeometry.kt` | Typy krawędzi i obliczenia na konkretnej krawędzi | Punkt i długość Béziera |
| `model/Polygon.kt`, `model/Scene.kt` | Wielokąt, scena i operacje edycji | Dodanie wierzchołka i zmiana typu krawędzi |
| `model/Constraints.kt`, `model/ConstraintSolver.kt` | Dane relacji i mechanizm ich rozwiązywania | Korekta długości i sprawdzenie prostopadłości |
| `model/BezierEditing.kt`, `model/HitTesting.kt` | Podział Béziera, translacja uchwytów, testy trafienia | Podział krzywej metodą de Casteljau |
| `model/CircularArc.kt` | Matematyka okręgu | Wyliczenie środka z końców i kąta |
| `model/DemoScene.kt` | Dane sceny startowej | Gwiazda z samoprzecięciami |
| `drawing/DrawingAlgorithms.kt` | Algorytmy niezależne od Compose | Bresenham, różnice skończone, clipping |
| `drawing/SceneRenderer.kt` | Przeliczenie geometrii na ekran i rysowanie | Wstawienie pikseli wybranych przez Bresenhama |
| `interaction/DragGesture.kt` | Bezwzględny cel ruchu i odstęp kliknięcia od punktu | Ruch po odmowie wcześniejszego zdarzenia nadal trafia w pozycję kursora |
| `ui/HelpDialog.kt`, `resources/help.md` | Dokumentacja dostępna offline w aplikacji | F1 otwiera instrukcję i opis algorytmów |

**Dlaczego ten podział?** Wzoru na promień czy funkcji Bresenhama nie trzeba testować przez klikanie w okno. Model nie zależy od widgetów Compose. Nie mamy osobnej klasy `ViewModel`; stan interfejsu przechowują funkcje Compose.

### 0.3. Punkt i wektor to różne rzeczy

Punkt opisuje położenie. Wektor opisuje przesunięcie, kierunek i długość. Oba mają dwie współrzędne, ale inne znaczenie.

```text
Point + Vector -> Point
Point - Vector -> Point
Point - Point  -> Vector
Vector + Vector -> Vector
Vector * liczba -> Vector
```

**Przykład:** `A=(2,3)`, `B=(8,7)`.

```text
B - A = (6,4)       — przesunięcie od A do B
A + (6,4) = B      — wykonanie tego przesunięcia
```

Nie definiujemy zwykłego `Point + Point`. Środek dwóch punktów zapisujemy jako `A + (B-A)*0.5`. To poprawna operacja afiniczna: położenie plus połowa przesunięcia.

### 0.4. Podstawowe operacje wektorowe

Dla `v=(x,y)` długość wynosi:

\[
|v|=\sqrt{x^2+y^2}.
\]

W kodzie używamy `hypot(x,y)`. Wektor jednostkowy to `v/|v|`.

**Przykład:** dla `(3,4)` długość to `5`, a normalizacja daje `(0.6,0.8)`. Po pomnożeniu przez `10` otrzymujesz `(6,8)`, czyli ten sam kierunek o długości `10`.

Normalizacja zerowego wektora nie ma sensu. Metoda `normalized()` sama nie sprawdza zera; kod wywołujący musi odrzucić taki przypadek tam, gdzie kierunek jest potrzebny.

Iloczyn skalarny:

\[
u\cdot v=u_xv_x+u_yv_y=|u||v|\cos\alpha.
\]

**Przykład:** `(3,4)·(-4,3)=-12+12=0`, więc wektory są prostopadłe. Iloczyn znormalizowanych zgodnych kierunków jest równy `1`, a przeciwnych `-1`.

Wektor prostopadły do `(x,y)` to `(-y,x)`. Zachowuje długość, bo `(-y)²+x²=x²+y²`.

Wyznacznik dwóch wektorów 2D:

\[
\det(u,v)=u_xv_y-u_yv_x.
\]

Opisuje podpisane pole równoległoboku i kierunek skrętu. **Przykład:** `det((1,0),(0,1))=1`, a po zamianie kolejności `-1`. Używamy go przy winding number i wyznaczaniu kąta przez `atan2`.

### 0.5. Najważniejsze konstrukcje Kotlina

| Konstrukcja | Znaczenie w projekcie | Przykład |
|---|---|---|
| `data class` | Obiekt danych z generowanym `equals`, `copy` itd. | `Point(2.0,3.0)` porównujemy po współrzędnych |
| `val` | Nie można przypisać innej wartości do tej właściwości | `point.x` nie zmieniamy bezpośrednio |
| `copy(...)` | Powstaje nowy obiekt z wybranymi zmienionymi polami | `vertex.copy(point = nowyPunkt)` |
| `sealed interface` | Zamknięty zestaw wariantów znany kompilatorowi | `Line`, `CubicBezier`, `CircularArc` |
| `when` | Rozdzielenie zachowania według wariantu | Osobne rysowanie odcinka i łuku |
| `enum class` | Zestaw nazwanych stałych | `G0`, `G1`, `C1` |
| `operator fun` | Czytelne operatory dla własnych typów | `point + delta` |
| `map` | Buduje nową listę z przekształconych elementów | Przesunięcie wszystkich wierzchołków |
| `filterNot` | Pomija elementy spełniające warunek | Usuwanie relacji zmienionej krawędzi |
| `T?` i `?.` | Wartość może nie istnieć | Nie znaleziono krawędzi o podanym ID |
| `?:` | Wartość zastępcza lub wcześniejszy powrót | Brak krawędzi -> odrzucenie operacji |
| `as?` | Bezpieczne rzutowanie; niezgodny typ daje `null` | Sprawdzenie, czy krawędź to Bézier |
| `companion object` | Funkcje fabrykujące i stałe związane z klasą | `CircularArcGeometry.fromTangent(...)` |
| funkcja rozszerzająca | Dodaje zapis wywołania bez zmiany klasy | `Vector.rotated(...)` |

`copy` jest płytkie: nie kopiuje automatycznie całego drzewa obiektów. Dlatego przy zmianie wierzchołka tworzymy nowe listy na odpowiednich poziomach przez `map`. Pola `List` są interfejsem listy tylko do odczytu, a nie ogólną gwarancją głębokiej niezmienności dowolnych obiektów przekazanych z zewnątrz. Nasze operacje stosują niezmienny styl aktualizacji.

`==` porównuje wartości, a `===` tożsamość obiektów. **Przykład:** `removeVertex` dla trójkąta zwraca dokładnie `this`; interfejs może rozpoznać brak operacji przez `updated === scene`.

### 0.6. Compose i przepływ aktualizacji

Stan, np. `scene`, przechowujemy przez `remember { mutableStateOf(...) }`. `remember` zachowuje go między rekompozycjami, a zapis nowej wartości informuje Compose, że widok wymaga aktualizacji. `by` pozwala wygodnie czytać i zapisywać wartość stanu.

**Przykład:** po udanym przesunięciu wykonujemy `scene = result.scene`. Compose odczytuje nową scenę i ponownie rysuje Canvas. Samo przerysowanie nie uruchamia od nowa sceny początkowej.

```text
zdarzenie myszy -> operacja modelu -> nowa scena lub odmowa
                                      |
                                      v
                              aktualizacja stanu Compose
                                      |
                                      v
                              renderowanie aktualnej sceny
```

<a id="etap-1"></a>

## 1. Etap 1 — wielokąty, edycja i wybór elementów

### 1.1. Topologia wielokąta i stabilne identyfikatory

Wielokąt ma uporządkowane listy `vertices` i `edges`. Krawędź o indeksie `i` łączy:

\[
V_i\longrightarrow V_{(i+1)\bmod n}.
\]

**Przykład:** dla `[A,B,C,D]` krawędzie to `A→B`, `B→C`, `C→D`, `D→A`. Ostatnia krawędź wraca na początek dzięki modulo.

Inwarianty sprawdzane w konstruktorach obejmują: minimum trzy wierzchołki, tyle samo krawędzi co wierzchołków, unikalne ID oraz poprawne odwołania relacji. `nextId` musi być większe od istniejących ID.

**Po co ID, skoro są indeksy?** Po wstawieniu wierzchołka indeksy dalszych elementów się zmieniają. Krawędź `#17` może zmienić indeks z `2` na `3`, ale relacja nadal wskazuje `#17`.

`EdgeGeometry` jest obliczanym widokiem krawędzi: zbiera ID końców, ich aktualne położenia i typ geometrii. Nie utrzymujemy drugiej niezależnej kopii końców w `Edge`, która mogłaby się rozjechać z wierzchołkami.

### 1.2. Przesuwanie wierzchołka

Podstawowy wzór:

\[
P'=P+\Delta.
\]

**Przykład:** `P=(100,80)`, `Δ=(15,-10)` daje `(115,70)`.

`tryMoveVertex` przygotowuje kopię geometrii i uruchamia solver. W obecnej wersji wynik może obejmować inne punkty z powodu relacji. Bez relacji zmienia się tylko wskazany wierzchołek. Zwykłe przesunięcie wierzchołka nie przesuwa automatycznie wszystkich jego uchwytów Béziera o tę samą deltę; ich pozycje wynikają z zapisanych danych i późniejszych korekt ciągłości.

### 1.3. Przesuwanie całego wielokąta

Do wszystkich wierzchołków dodajemy tę samą deltę. Punkty kontrolne Béziera również przesuwamy. Dla łuku przesuwają się końce, więc środek obliczy się w nowej pozycji.

**Przykład:** trójkąt `[(0,0),(10,0),(0,10)]` po `Δ=(3,4)` ma punkty `[(3,4),(13,4),(3,14)]`. Odległości i kąty wewnątrz figury nie zmieniają się.

Jeśli wielokąt ma relację z innym wielokątem, solver może poruszyć także element tamtej figury. Nie mamy globalnej zasady „wszystkie inne wielokąty są nieruchome”.

### 1.4. Wstawianie wierzchołka i usuwanie wierzchołka

Dla odcinka nowy punkt to:

\[
M=A+\frac12(B-A).
\]

**Przykład:** `(2,4)` i `(10,8)` dają środek `(6,6)`. Z `A→B` powstaje `A→M→B`. Pierwsza część zachowuje ID starej krawędzi, druga dostaje nowe ID.

Usunięcie `B` z `A→B→C` daje `A→C`. Usuwamy wychodzącą krawędź `B→C`; przychodząca zachowuje swoje ID, ale ma nowy koniec. Przy minimum trzech wierzchołkach dalsze usuwanie jest blokowane.

**Przykład topologiczny:** jeśli relacja długości dotyczyła `A→B`, to po zmianie w `A→C` stara relacja nie jest już automatycznie sensowna. Dlatego usuwamy relacje obu zmienianych krawędzi. Sama stabilność ID nie oznacza, że zachowujemy dawną interpretację relacji po zmianie topologii.

Od punktów 3–4 podział zależy od typu krawędzi: Bézier jest dzielony de Casteljau, a łuk w połowie kąta. Po zmianach topologii solver ponownie sprawdza scenę. Jeśli się nie uda, operacja zwraca starą scenę.

### 1.5. Dwa układy współrzędnych

Model ma logiczną scenę `900×470`. Canvas może mieć inny rozmiar. Dobieramy jednolitą skalę:

\[
s=\min\left(\frac{W}{900},\frac{H}{470}\right).
\]

Wyśrodkowanie:

\[
l=\frac{W-900s}{2},\qquad q=\frac{H-470s}{2}.
\]

Przejście na ekran:

\[
x_e=sx+l,\qquad y_e=sy+q.
\]

Odwrotnie przy myszy odejmujemy przesunięcie i dzielimy przez skalę.

**Przykład:** Canvas `1800×1000` daje `s=2`, `l=0`, `q=30`. Punkt sceny `(100,50)` jest na ekranie w `(200,130)`. Ruch myszy o `(12,-8)` pikseli oznacza ruch modelu o `(6,-4)`.

Jednakowa skala obu osi zachowuje kąty i okręgi. Przy różnych skalach X i Y okrąg stałby się elipsą, a prostopadłość na ekranie mogłaby wyglądać inaczej.

### 1.6. Hit testing — co kliknięto?

Sprawdzamy kolejno wierzchołki, uchwyty, krawędzie, a na końcu wnętrza. W każdej kategorii figury przeglądamy od końca listy, preferując później rysowane figury. Priorytet kategorii jest globalny: wierzchołek może wygrać z wnętrzem innej figury.

**Przykład:** kliknięcie obok wierzchołka nie powinno przypadkowo wybierać całego wielokąta. Dlatego punkt ma pierwszeństwo przed wnętrzem.

Tolerancja na ekranie wynosi około `10` pikseli. W modelu używamy `10/s`. **Przykład:** przy skali `2` promień trafienia to `5` jednostek sceny, nadal `10` pikseli ekranowych.

Przeciąganie przechowuje obiekt wybrany przy naciśnięciu (`dragGesture.target`) i odstęp kliknięcia od punktu odniesienia (`grabOffset`). Ruch kursora nie wybiera co klatkę nowego obiektu. Oddzielna lista `selectedEdgeIds` przechowuje do dwóch krawędzi dla relacji. Kliknięcie już zaznaczonej krawędzi usuwa ją z tej listy.

### 1.7. Odległość punktu od odcinka — rzut i clamp

Dla kliknięcia `Q` i odcinka `A→B` liczymy `d=B-A`:

\[
t=\frac{(Q-A)\cdot d}{d\cdot d}.
\]

Następnie ograniczamy `t` do `[0,1]`, czyli wykonujemy clamp, a najbliższy punkt to `A+t*d`. Wynikiem jest odległość kliknięcia od tego punktu.

**Przykład:** `A=(0,0)`, `B=(10,0)`, `Q=(4,3)`.

```text
t = 40/100 = 0.4
punkt najbliższy = (4,0)
odległość = 3
```

Dla `Q=(14,3)` surowe `t=1.4`; po clampie `1`. Najbliższy jest koniec `(10,0)`, odległość wynosi `5`. Bez clampu liczylibyśmy odległość od nieskończonej prostej, a nie od odcinka.

Jeśli końce odcinka są identyczne, odległość liczymy bez dzielenia jako `|Q-A|`.

Krzywe do hit testingu przybliżamy 40 odcinkami i bierzemy minimalną odległość od nich. **Przykład:** kliknięcie w wybrzuszenie łuku może trafić w krawędź, chociaż jest daleko od jej cięciwy.

### 1.8. Wnętrze wielokąta i samoprzecięcia: winding number

Obecnie używamy niezerowej liczby nawinięć. Liczymy podpisane przecięcia poziomego promienia z brzegiem. Przecięcia w przeciwnych kierunkach mają przeciwne znaki. Punkt uznajemy za wewnętrzny, gdy suma jest różna od zera.

Do określenia strony krawędzi służy:

\[
\operatorname{side}(A,B,Q)=(B_x-A_x)(Q_y-A_y)-(B_y-A_y)(Q_x-A_x).
\]

Połowicznie otwarte warunki na współrzędne Y zapobiegają podwójnemu liczeniu wspólnego wierzchołka przecinanych krawędzi. Współrzędne ekranu mogą odwrócić znak nawinięcia względem rysunku kartezjańskiego, ale warunek „niezero” pozostaje taki sam.

**Przykład:** środek naszej gwiazdy ma liczbę nawinięć o wartości bezwzględnej `2`. Reguła even–odd potraktowałaby `2` jako parzyste i zostawiła dziurę. Non-zero uznaje `2 != 0`, więc środek jest wypełniony i klikalny. Odwrócenie kolejności wierzchołków zmienia znak `2` na `-2`, ale wynik pozostaje wewnętrzny.

Renderer używa tej samej reguły `PathFillType.NonZero`. Nie obliczamy jawnie wszystkich samoprzecięć i nie budujemy otoczki wypukłej. W ogólnych samoprzecinających się konturach obie reguły wypełniania są poprawnymi, różnymi definicjami wnętrza; wybraliśmy non-zero zgodnie z oczekiwanym wyglądem.

<a id="etap-2"></a>

## 2. Etap 2 — ograniczenia geometryczne

### 2.1. Jak zapisujemy relacje

`LengthConstraint` zawiera ID krawędzi i docelową długość. `PerpendicularConstraint` zawiera ID dwóch krawędzi. Same relacje też mają ID, aby można je było usuwać z listy.

**Przykład:** `LengthConstraint(..., edgeId=17, desiredLength=120)` oznacza, że krawędź `#17` ma długość `120` w jednostkach sceny, a nie stałą liczbę pikseli ekranowych.

Długość można zmienić: dodanie nowej długości tej samej krawędzi zastępuje poprzednią. Inne typy relacji tej krawędzi pozostają. Powtórna prostopadłość tej samej nieuporządkowanej pary jest odrzucana. Można łączyć krawędzie różnych wielokątów, bo odwołania są globalne przez ID.

### 2.2. Narzucona długość odcinka

Dla początku `A`, końca `B` i długości docelowej `L`:

\[
B'=A+\frac{B-A}{|B-A|}L.
\]

**Przykład:** `A=(0,0)`, `B=(3,4)`, obecna długość `5`, żądana `10`.

```text
kierunek jednostkowy = (0.6,0.8)
nowe B = (6,8)
```

Bez aktywnego przeciągania domyślnie zostawiamy początek, zachowujemy kierunek i zmieniamy koniec. Jeśli użytkownik przeciąga koniec, zachowujemy go, a poprawiamy początek: `A'=B-normalize(B-A)*L`. Dzięki temu relacja długości nie cofa automatycznie punktu spod kursora. Przy propagacji przez sąsiednie relacje preferujemy zachowanie wspólnego punktu z krawędzią o wyższym priorytecie. Nie ma unikalnego geometrycznie sposobu spełnienia długości: implementacja musi wybrać regułę korekty.

Długość musi być skończoną liczbą większą od `EPSILON`. Dla zerowej krawędzi nie ma kierunku normalizacji, więc taka korekta jest odrzucana. Dialog jako wartość początkową pokazuje aktualną długość; akceptujemy przecinek albo kropkę dziesiętną.

### 2.3. Narzucona prostopadłość

Matematycznie:

\[
u\cdot v=0.
\]

Obie krawędzie mają równoważną rolę w relacji. Dla każdej operacji wybieramy tymczasową krawędź odniesienia na podstawie aktualnej edycji. Jej kierunek normalizujemy i obracamy o 90°. Drugą krawędź dopasowujemy do takiego kierunku, zachowując jej długość cięciwy. Role nie są utrwalane przez kolejność kliknięcia pary.

**Przykład interakcji:** `A=(0,0)`, `B=(10,0)`, `C=(10,5)` i `AB ⟂ BC`. Gdy przeciągasz `C` do `(13,4)`, to `BC` steruje kierunkiem. `C` pozostaje w nowej pozycji, a wolny koniec `A` krawędzi `AB` zostaje przestawiony tak, aby zachować kąt prosty. Przy przeciąganiu `A` role odwracają się.

Gdy nie ma aktywnej edycji albo obie krawędzie mają jednakowy priorytet, remis rozstrzygamy przez stabilne ID. Zapewnia to taki sam początkowy wynik po wybraniu pary w odwrotnej kolejności. Nie oznacza symetrycznego rozdzielenia obrotu po połowie ani globalnego minimum przesunięć.

**Przykład:** kierunek pierwszej krawędzi `(3,4)` daje kierunek prostopadły `(-4,3)`. Po normalizacji jest to `(-0.8,0.6)`. Dla drugiej krawędzi zaczynającej się w `(10,0)` i mającej cięciwę długości `5` jeden możliwy nowy koniec to `(6,3)`.

Możliwe są dwa zwroty: `(-0.8,0.6)` i `(0.8,-0.6)`. Wybieramy bliższy bieżącemu kierunkowi drugiej krawędzi: jeśli iloczyn skalarny z dotychczasowym kierunkiem jest ujemny, odwracamy wektor. Ogranicza to niepotrzebne przeskakiwanie kształtu na drugą stronę.

**Wspólny wierzchołek:** nie należy przesuwać wspólnego punktu, aby dopasować drugą krawędź, bo zmieniłoby to również krawędź odniesienia. Przesuwamy wolny koniec. Gdy wolny jest początek, obliczamy go jako `koniec - nowyKierunek * długość`.

**Przykład:** krawędź odniesienia to `B→C`, a korygowana `A→B`. Wspólne `B` pozostaje na miejscu, przesuwamy `A`.

**Pośrednie współdzielenie punktu:** wybór kotwicy uwzględnia też inne krawędzie grafu. Jeśli korygowana krawędź ma koniec należący do krawędzi bliższej źródłu ruchu, zachowujemy ten koniec. Chronimy również wspólny punkt dwóch krawędzi o jednakowej odległości od źródła. Inaczej korekta jednej relacji mogłaby przestawić punkt odniesienia innej i uruchomić wzajemne poprawianie geometrii.

**Przykład z gwiazdy:** `#11 ⟂ #8` oraz `#8 ⟂ #10` wymuszają `#11 ∥ #10`. W 2D obie niezerowe krawędzie prostopadłe do tego samego kierunku muszą być równoległe. Ponieważ `#11` i `#10` mają wspólny wierzchołek, leżą na jednej prostej, a nawet mogą nałożyć się na siebie. To dopuszczalny wynik tych relacji. Błędem było dodatkowe przesuwanie ich punktów odniesienia przez kolejne korekty, prowadzące do dryfu przy przeciąganiu. Aby odzyskać załamanie w tym wierzchołku, trzeba usunąć jedną z tych relacji i przesunąć punkt.

Dla krzywych prostopadłość dotyczy **cięciw**, zgodnie z przyjętą interpretacją projektu. Nie oznacza prostopadłości stycznych ani „całych krzywych”.

### 2.4. Dlaczego potrzebujemy solvera iteracyjnego

Jedna korekta może popsuć inną relację, szczególnie przy współdzielonych punktach. Solver wykonuje sekwencję projekcji/korekt na geometrii podobną ideowo do relaksacji Gaussa–Seidla; nie rozwiązuje globalnego układu metodą Newtona ani przez dokładną eliminację równań.

Obecna kolejność:

```text
sprawdź dozwolone ustawienia ciągłości łuków
wyznacz priorytety krawędzi dla aktualnej edycji przez BFS
16 razy:
    zastosuj relacje długości i prostopadłości w kolejności listy
    przelicz łuki
    skoryguj ciągłość Bézierów
sprawdź wszystkie warunki
zwróć nową scenę albo odmowę
```

**Przykład:** `AB` ma długość `10` i jest prostopadłe do `BC`. Po przesunięciu `B` korekta długości może poruszyć `B`; potem dopasowanie prostopadłości przesuwa wolny koniec `C`. Jeśli istnieje również relacja `CD`, następna korekta może przenieść ruch dalej.

Program wykonuje dokładnie 16 przebiegów, a nie „maksymalnie 16 z wcześniejszym wyjściem po zbieżności”. Wcześniejszy powrót następuje przy niemożności wykonania korekty. Po przebiegach jest walidacja.

**Priorytety i BFS:** w tymczasowym `ConstraintEdit` przekazujemy ID bezpośrednio przesuwanych wierzchołków i krawędzi związanych z edycją. Budujemy graf, którego węzły to krawędzie sceny, a połączenia to relacje prostopadłości. Wyszukujemy jego spójne składowe, czyli grupy połączone relacjami. W każdej wybieramy jedno źródło: krawędź aktywnej edycji o najmniejszym ID, a jeśli takich nie ma — krawędź o najmniejszym ID w grupie. Źródło dostaje odległość 0. Przeszukiwanie wszerz (BFS) oblicza najkrótszą liczbę relacji od źródła do pozostałych krawędzi. Mniejsza odległość oznacza silniejszy priorytet. Te dane istnieją tylko na czas jednej operacji. Aktywne krawędzie bez relacji prostopadłości zachowują priorytet 0 na potrzeby wyboru kotwicy przy korekcie długości.

**Przykład łańcucha:** `e1 ⟂ e2` i `e2 ⟂ e3`, użytkownik edytuje `e3`. Priorytety to `e3:0`, `e2:1`, `e1:2`. W relacji `e2–e3` dopasowujemy `e2`, a w relacji `e1–e2` dopasowujemy `e1`. Bez propagacji priorytetu dwie relacje mogłyby na zmianę obracać `e2` według przeciwnych źródeł.

BFS dla tego grafu ma koszt `O(Eg + C)`, gdzie `Eg` to liczba jego węzłów, a `C` relacji. To organizacja kierunku korekt, nie globalne rozwiązanie wszystkich równań. Przy wspólnym przesuwanym wierzchołku obie jego krawędzie są kandydatami na źródło; wybieramy jedną, aby dwa różne kierunki wejściowe nie sterowały równocześnie tą samą grupą. Przeciągany punkt jest osobno uwzględniany jako kotwica korekty. Relację, która już mieści się w tolerancji, pozostawiamy bez ponownego przesuwania punktów.

**Bardzo ważna odpowiedź na obronie:** brak zbieżności po 16 przebiegach nie jest dowodem matematycznej sprzeczności. Wynik może zależeć od kolejności korekt i wyboru poruszanego punktu. Komunikat o sprzecznych relacjach jest uproszczony. Ta implementacja nie jest pełnym solverem CAD.

### 2.5. Transakcyjność i wynik operacji

Operacje typu `tryMoveVertex` i dodawanie relacji zwracają:

```kotlin
ConstraintUpdate.Applied(scene)
ConstraintUpdate.Rejected(reason)
```

Zmiany liczymy na nowej scenie. UI przypisuje ją dopiero po powodzeniu. Po odmowie stary model pozostaje bez zmiany.

**Przykład:** próba ustawienia drugiego G1 na jednym łuku tworzy kandydaturę sceny, ale walidacja odrzuca ją. Pierwszy koniec nadal ma G1, drugi zachowuje poprzednią klasę.

Wygodne metody `moveVertex` i `movePolygon` zwracają przy odmowie starą scenę. Interfejs używa wersji `try...`, bo potrzebuje przyczyny do komunikatu.

### 2.6. Tolerancja numeryczna i stopnie swobody

`EPSILON = 1e-9` służy do wykrywania bardzo małych długości. `CONSTRAINT_TOLERANCE = 1e-5` jest tolerancją spełnienia relacji. To różne zadania.

**Przykład:** długość `100.0000001` może być uznana za równą `100`, ponieważ błąd `1e-7` jest mniejszy od tolerancji. Nie porównujemy wyników zmiennoprzecinkowych wyłącznie przez dokładne `==`.

Dla prostopadłości sprawdzamy znormalizowany iloczyn skalarny, więc miara jest niezależna od długości krawędzi. Tolerancja iloczynu skalarnego nie jest bezpośrednio tolerancją kąta w radianach.

Pojedynczy wierzchołek 2D ma dwie współrzędne, czyli dwa stopnie swobody. Długość wnosi jedno równanie, prostopadłość pary drugie. Samo liczenie równań nie dowodzi istnienia rozwiązania: ograniczenia mogą być zależne lub sprzeczne.

**Przykład sprzeczności teoretycznej:** trzy niezerowe kierunki w 2D nie mogą być parami prostopadłe. Program może odrzucić taki układ po niespełnieniu walidacji; nie buduje formalnego dowodu, które relacje są winne.

### 2.7. Długość krzywej i relacje po zmianach topologii

Dla Béziera skalujemy oba uchwyty i koniec względem początku współczynnikiem `s = żądanaDługość / bieżącaDługość`. Jednolite skalowanie mnoży wszystkie odległości, także długość krzywej, przez `s`. Następne korekty ciągłości mogą jednak ponownie wpłynąć na krzywą — dlatego wracamy do solvera.

**Przykład:** gdy zmierzona długość wynosi `50`, a żądana `100`, wektory od początku do obu uchwytów i końca mnożymy przez `2`.

Dla łuku skaluje się długość cięciwy przy zachowaniu kąta. Przy G1 na końcu pozostawiamy ten koniec i poruszamy początkiem, aby nie zmieniać sąsiedniej stycznej.

Podział krawędzi usuwa relacje dotyczące starej krawędzi. Usunięcie wierzchołka usuwa relacje krawędzi przychodzącej i wychodzącej. Usunięcie samej relacji nie odtwarza wcześniejszego kształtu — uwalnia geometrię do przyszłej edycji.

**Przykład:** po usunięciu relacji długości `100` krawędź nadal ma w tej chwili długość `100`; dopiero ruch może ją zmienić.

<a id="etap-3"></a>

## 3. Etap 3 — sześcienne segmenty Béziera

### 3.1. Cztery punkty i wzór Bernsteina

Sześcienny Bézier ma cztery punkty: początek `P0`, uchwyty `P1`, `P2` i koniec `P3`.

\[
B(t)=(1-t)^3P_0+3(1-t)^2tP_1+3(1-t)t^2P_2+t^3P_3,
\qquad 0\leq t\leq1.
\]

Współczynniki to wielomiany bazowe Bernsteina stopnia 3. Ich suma wynosi `1`, więc wzór jest kombinacją afiniczną punktów. Dla `t` w `[0,1]` wagi są nieujemne: krzywa leży w otoczce wypukłej punktów kontrolnych.

**Przykład używany dalej:**

```text
P0=(0,0), P1=(0,3), P2=(3,3), P3=(3,0)
```

Dla `t=0.5` wagi to `1/8, 3/8, 3/8, 1/8`:

\[
B(0.5)=(1.5,2.25).
\]

Dla `t=0` dostajemy dokładnie `P0`, dla `t=1` dokładnie `P3`. Krzywa zwykle nie przechodzi przez uchwyty `P1` i `P2`.

Model zapisuje ten wzór przez różnice punktów:

\[
B(t)=P_0+3(1-t)^2t(P_1-P_0)+3(1-t)t^2(P_2-P_0)+t^3(P_3-P_0).
\]

To równoważna postać pasująca do rozróżnienia `Point`/`Vector`.

### 3.2. Dlaczego stopień trzeci i czym jest wielobok kontrolny?

Stopień wynosi 3, bo najwyższa potęga parametru to `t³`. Dla Béziera stopnia `n` używamy `n+1` punktów kontrolnych. Dwa wewnętrzne uchwyty sześciennej krzywej pozwalają osobno sterować stycznymi na początku i końcu.

Wielobok kontrolny w tym zadaniu rysujemy jako otwartą łamaną:

```text
P0 -> P1 -> P2 -> P3
```

Są trzy odcinki pomocnicze. Nie dodajemy `P3→P0`. W aplikacji linie są przerywane, a uchwyty można przeciągać.

**Przykład:** przeciągnięcie `P1` z `(0,3)` do `(-2,3)` zmienia kierunek wychodzenia krzywej z `P0`, choć `P0` i `P3` pozostają tymi samymi końcami.

### 3.3. Przełączanie odcinka w Béziera

Uchwyty tworzymy na cięciwie w `1/3` i `2/3` jej długości:

\[
P_1=A+\frac13(B-A),\qquad P_2=A+\frac23(B-A).
\]

**Przykład:** odcinek od `(0,0)` do `(9,0)` dostaje uchwyty `(3,0)` i `(6,0)`. Taka krzywa opisuje dokładnie odcinek `B(t)=A+t(B-A)`.

Następnie solver uwzględnia domyślne C1 na końcach, więc końcowy kształt po konwersji może się od razu wygiąć, aby dopasować się do sąsiednich krawędzi. Stwierdzenie „po konwersji zawsze widzę prostą” byłoby nieprawdziwe przy aktywnej ciągłości.

Ustawienie ciągłości ma flagę `explicit`: wybór użytkownika jest odróżniany od wartości domyślnej. Nowy Bézier ma domyślnie C1, jeśli użytkownik wcześniej jawnie nie wybrał klasy; przy sąsiednim łuku trzeba respektować dostępne G0/G1. Konwersje z/do łuku dodatkowo dostosowują niedozwolone dla łuku C1 do G0. Nie zachowujemy kształtu okręgu przez zwykłe przełączenie łuku w pojedynczy Bézier; powstaje nowa geometria.

### 3.4. Pochodna i styczne

Pochodna krzywej:

\[
B'(t)=3(1-t)^2(P_1-P_0)+6(1-t)t(P_2-P_1)+3t^2(P_3-P_2).
\]

Na końcach:

\[
B'(0)=3(P_1-P_0),\qquad B'(1)=3(P_3-P_2).
\]

**Przykład:** dla naszej krzywej `B'(0)=(0,9)`, a `B'(1)=(0,-9)`. Początek i koniec mają styczne pionowe o przeciwnych kierunkach przejścia.

Kod przechowuje w `startTangent` i `endTangent` wektory uchwytów bez czynnika `3`. Dla porównania dwóch sześciennych segmentów wspólny czynnik się skraca. Nie należy nazywać tych wartości pełnymi pochodnymi bez zastrzeżenia. Dla łuków te właściwości zwracają kierunki jednostkowe; C1 przy łukach jest zabronione, więc nie porównujemy ich długości z uchwytami Béziera.

### 3.5. G0, G1 i C1 — definicje i konkretne przykłady

Niech wspólny punkt dwóch segmentów będzie `P`. Ostatni uchwyt segmentu przychodzącego to `Cin`, a pierwszy uchwyt wychodzącego `Cout`.

| Klasa | Warunek | Przykład dla `P=(0,0)` |
|---|---|---|
| G0 | Te same końce segmentów w `P` | Uchwyty mogą być `(-2,0)` i `(0,5)`; powstaje narożnik |
| G1 | `P-Cin` i `Cout-P` mają ten sam kierunek i zwrot | `Cin=(-2,0)`, `Cout=(5,0)` |
| C1 | Pochodne na połączeniu są równe | `Cin=(-2,0)`, `Cout=(2,0)` |

Dla dwóch sześciennych segmentów o lokalnym parametrze `[0,1]` warunek C1 upraszcza się do:

\[
P-C_{in}=C_{out}-P.
\]

Dlatego uchwyty dla C1 są lustrzane względem `P`. Dla G1 leżą na tej samej prostej po przeciwnych stronach `P`, ale ich odległości mogą się różnić.

G1 sprawdzamy przez zgodność znormalizowanych wektorów. W implementacji używamy znormalizowanego iloczynu skalarnego bliskiego `1`. Dla C1 dodatkowo sprawdzamy małą długość różnicy wektorów.

**Dlaczego G1 nie wystarcza do C1?** W przykładzie długości wektorów uchwytów to `2` i `5`, a pochodnych `6` i `15`. Kierunek jest gładki, ale zmienia się prędkość parametryzacji.

**Parametryzacja ma znaczenie:** C1 dotyczy pochodnych względem konkretnego parametru. Przy zmianie długości przedziałów parametru trzeba uwzględnić skalowanie pochodnych. W naszym modelu każdy segment używa własnego `[0,1]`.

### 3.6. C1 między odcinkiem a krzywą — skąd czynnik 3?

Odcinek `A→P` traktujemy jak szczególny sześcienny Bézier z uchwytami w `1/3` i `2/3`. Jego pochodna jest równa `P-A`, ale wektor od ostatniego wirtualnego uchwytu do `P` wynosi `(P-A)/3`.

Dla wychodzącego Béziera C1 wymaga:

\[
3(C_{out}-P)=P-A.
\]

**Przykład:** `A=(-12,0)`, `P=(0,0)`. Pierwszy uchwyt krzywej powinien być `(4,0)`, ponieważ `3*(4,0)=(12,0)`.

To częste pytanie na obronie: nie należy ustawiać uchwytu w odległości `12`, jeśli odcinek ma długość `12`. Dla C1 przy tej parametryzacji uchwyt ma odległość `4`.

### 3.7. Co porusza się przy przeciąganiu uchwytu?

Przeciągany uchwyt traktujemy jako źródło kierunku. Najpierw propagujemy jego zmianę na sąsiedni element, a następnie uruchamiamy solver wszystkich relacji.

**G0:** przy `P1=(0,3)` ruch o `(1,0)` daje `P1=(1,3)` bez wymuszania stycznej sąsiada.

**G1, sąsiedni Bézier:** obracamy jego uchwyt do zgodnego kierunku, zachowując długość tamtego uchwytu. Jeśli nowy kierunek wynosi `(3,4)`, a długość sąsiedniego uchwytu wynosi `10`, używamy wektora `(6,8)`.

**C1, sąsiedni Bézier:** kopiujemy wektor uchwytu na drugą stronę wspólnego punktu. Dla `P=(0,0)` i wychodzącego uchwytu `(3,2)` poprzedni uchwyt trafia w `(-3,-2)`.

**C1, sąsiedni odcinek:** musi dostosować się jego drugi koniec. Dla wychodzącego uchwytu `(3,2)` i `P=(0,0)` początek odcinka ustawiamy na `P-3*(3,2)=(-9,-6)`. Pochodna odcinka to wtedy `(9,6)`, tak samo jak Béziera.

**G1, sąsiedni odcinek:** zachowujemy jego długość, zmieniamy kierunek. Dla długości `10` i kierunku `(3,4)` początek poprzedzającego odcinka będzie `P-(6,8)`.

Gdy sąsiadem jest łuk, nie przesuwamy jego końca w tej propagacji — łuk dopasuje swój kąt do nowej stycznej w `enforceArcs()`.

**Ograniczenie:** późniejsze globalne korekty mogą jeszcze zmienić uchwyt albo odrzucić ruch. Nie mamy uniwersalnego solvera z twardym warunkiem „przeciągany punkt zawsze dokładnie pod kursorem”.

### 3.8. Podział de Casteljau

Podział krzywej w `t=0.5` wykonujemy przez kolejne środki:

```text
Q0 = środek(P0,P1)
Q1 = środek(P1,P2)
Q2 = środek(P2,P3)
R0 = środek(Q0,Q1)
R1 = środek(Q1,Q2)
S  = środek(R0,R1)
```

Powstają segmenty `[P0,Q0,R0,S]` i `[S,R1,Q2,P3]`.

**Przykład:** dla punktów `(0,0),(0,3),(3,3),(3,0)`:

```text
Q0=(0,1.5)    Q1=(1.5,3)    Q2=(3,1.5)
R0=(0.75,2.25)             R1=(2.25,2.25)
S=(1.5,2.25)
```

Wspólne pochodne nowych segmentów to `(2.25,0)`, więc mają C1 przy równych połowach parametru. Nowy wierzchołek otrzymuje domyślnie C1.

Ważna różnica: `t=0.5` jest połową przedziału parametru, ale dla dowolnego Béziera nie musi być połową długości łuku. Nasz symetryczny przykład ma ten szczególny przypadek.

Sam de Casteljau zachowuje kształt dokładnie w arytmetyce rzeczywistej. Późniejszy solver może zmienić wynik, jeśli trzeba dopasować zewnętrzne relacje C1 lub długości. Podział zmienia też skalę lokalnych pochodnych: dla połówek każda ma pochodne przeskalowane względem pierwotnego parametru.

### 3.9. Numeryczna długość Béziera

Teoretycznie długość wynosi:

\[
L=\int_0^1 |B'(t)|\,dt.
\]

Nie stosujemy tu całkowania symbolicznego. Sumujemy 40 odcinków pomiędzy próbkami:

\[
L_{40}=\sum_{i=1}^{40}\left|B(i/40)-B((i-1)/40)\right|.
\]

**Przykład:** krzywa z punktów `(0,0),(0,3),(3,3),(3,0)` ma cięciwę długości `3`, ale jej rzeczywista długość wynosi `6`. Suma 40 odcinków daje około `5.99853`. Długość po łamanej jest krótsza od gładkiego łuku.

Nie myl dwóch parametrów jakości: pomiar długości używa 40 próbek w modelu, a rysowanie z punktu 5 dobiera znacznie więcej kroków zależnie od ekranu. Mały błąd solvera względem `L40` nie oznacza tak samo małego błędu względem rzeczywistej całki.

<a id="etap-4"></a>

## 4. Etap 4 — łuki okręgów

### 4.1. Kąt środkowy i reprezentacja łuku

Kąt środkowy ma wierzchołek w środku okręgu i ramiona biegnące do końców łuku. Nasz `sweep` jest podpisanym kątem przejścia od początku do końca, więc określa także kierunek obrotu i może opisywać łuk większy od półokręgu.

**Przykład:** `π/2` to `90°`, `π` to `180°`, `3π/2` to `270°`. Tę samą parę końców może łączyć krótki i długi łuk. Sam promień i dwa końce nie wystarczą bez wyboru odpowiedniej części okręgu.

W `EdgeShape.CircularArc` przechowujemy tylko `sweep`. `CircularArcGeometry` łączy go z aktualnymi końcami i wylicza resztę. Domyślnie oba końce mają G0. Poprzednie C1 przy konwersji jest zastępowane przez G0. Jeśli zachowane G1 występowałoby na obu końcach, konwersja jest odrzucana.

### 4.2. G0 na obu końcach: półokrąg

Wymaganie `r=|B-A|/2` oznacza, że cięciwa jest średnicą. Ustawiamy `sweep=π`.

**Przykład:** `A=(0,0)`, `B=(100,0)`:

```text
środek O=(50,0)
promień r=50
długość łuku = 50π ≈ 157.07963
punkt w połowie = (50,-50)
```

Ostatnia współrzędna opisuje nasz dodatni `sweep=π`: dla poziomej cięciwy biegnącej w prawo wybrany półokrąg przechodzi nad nią na ekranie. Odwrócenie kolejności końców zmienia stronę domyślnego półokręgu. Nie mamy osobnego przycisku wyboru strony przy G0.

### 4.3. Wyprowadzenie środka z końców i kąta

Niech `d=B-A`, `c=|d|`, `M=A+d/2`. Środek `O` leży na symetralnej cięciwy. W podpisanej odległości `h` od `M`:

\[
h=\frac{c}{2\tan(\theta/2)}.
\]

Wynika to z podziału trójkąta równoramiennego `AOB` na dwie części. Wektor `d.perpendicular()` ma długość `c`, więc:

\[
O=A+\frac12d+\frac{d^\perp}{2\tan(\theta/2)}.
\]

Kod:

```kotlin
start + chord * 0.5 + chord.perpendicular() * (0.5 / tan(sweep * 0.5))
```

**Przykład:** `A=(0,0)`, `B=(100,0)`, `θ=π/2`. Mamy `d=(100,0)`, `d⊥=(0,100)` i `tan(π/4)=1`, więc `O=(50,50)`. Promień wynosi `√(50²+50²)=50√2≈70.71068`.

Dla kąta większego od π przesunięcie może zmienić znak. Dlatego kod używa podpisanego kąta, a nie wyłącznie wartości bezwzględnej.

### 4.4. Punkt na łuku przez obrót

Obracamy wektor promienia od `O` do `A`:

\[
P(t)=O+R(t\theta)(A-O).
\]

Macierz obrotu daje:

\[
(x,y)\mapsto(x\cos\alpha-y\sin\alpha,\;x\sin\alpha+y\cos\alpha).
\]

**Przykład:** dla ćwierćokręgu z poprzedniego podrozdziału `t=0.5` daje obrót o `45°` i punkt około `(50,-20.71068)`. Dla półokręgu od `(0,0)` do `(100,0)` punkt w połowie to `(50,-50)`.

Równomierne przyrosty `t` dają równomierne przyrosty kąta, a więc długości łuku okręgu. To różni okrąg od ogólnego Béziera.

### 4.5. Styczne łuku i wyznaczanie kąta dla G1

Styczna do okręgu jest prostopadła do promienia. Kierunki stycznych na obu końcach możemy też wyrazić przez kierunek cięciwy:

\[
t_A=\operatorname{normalize}(R(-\theta/2)d),\quad
t_B=\operatorname{normalize}(R(\theta/2)d).
\]

**Przykład:** dla `d=(100,0)` i `θ=90°` dostajemy kierunki pod kątami `-45°` i `+45°`.

Jeśli znamy wymaganą styczną na początku:

\[
\theta=2\operatorname{angle}(t_A,d).
\]

Jeśli znamy styczną na końcu:

\[
\theta=2\operatorname{angle}(d,t_B).
\]

Podpisany kąt między wektorami obliczamy jako:

\[
\operatorname{angle}(u,v)=\operatorname{atan2}(\det(u,v),u\cdot v).
\]

`atan2` zachowuje informację o kierunku i ćwiartce. Sam `acos` z iloczynu skalarnego nie dałby znaku obrotu.

**Przykład:** cięciwa w prawo `(1,0)`, styczna początkowa `(1,-1)`. Kąt od stycznej do cięciwy to `45°`; podwajamy go i otrzymujemy łuk `90°`. Dla jego promienia `50√2` długość wynosi około `111.07207`.

Po przesunięciu sąsiedniego wierzchołka albo uchwytu zmienia się styczna. `enforceArcs` pobiera nowy kierunek sąsiada i wylicza nowy `sweep`. Końce łuku mogą pozostać te same, choć zmieniają się promień i środek.

### 4.6. Dlaczego tylko jeden koniec z G1?

To jawny wymóg zadania. Geometrycznie dwa końce i styczna na jednym końcu wyznaczają już okrąg; kierunek na drugim końcu wynika z tej geometrii. Dwa dowolne zadane kierunki zwykle nie są zgodne z jednym okręgiem.

**Przykład:** dla `A=(0,0)`, `B=(100,0)` styczna początkowa pod `-45°` wyznacza łuk `90°`, którego styczna końcowa ma `+45°`. Nie można niezależnie wymagać na końcu kierunku `+20°`, zachowując te końce i początkową styczną.

W szczególnych zgodnych konfiguracjach G1 na obu końcach byłoby matematycznie możliwe. Aplikacja i tak blokuje drugi wybór zgodnie ze specyfikacją. Nie należy mówić „G1 na obu końcach okręgu jest zawsze matematycznie niemożliwe”.

Interfejs wyłącza przycisk przez `canSetContinuity`, a model sprawdza to osobno. Aby zmienić koniec z G1, najpierw ustawiamy na poprzednim G0.

### 4.7. Degeneracje i długość łuku

Gdy końce są identyczne, w tej reprezentacji nie wybieramy arbitralnego okręgu. Gdy zadana styczna jest równoległa do cięciwy, nie ma skończonego okręgu przechodzącego przez dwa różne końce i mającego taką styczną. Przy zbliżaniu się do tego układu promień rośnie do bardzo dużych wartości.

Kod odrzuca kąty bliskie `0` i `±2π`, z progiem `1e-7`. To nie tylko sprawdzanie teoretycznej równości — ma też chronić przed źle uwarunkowaną geometrią.

**Przykład:** `A=(0,0)`, `B=(100,0)`, styczna `(1,0)` daje kąt `0`. Nie wyznaczamy promienia przez dzielenie przez `tan(0)`; operacja zostaje odrzucona.

Długość łuku liczymy analitycznie:

\[
L=r|\theta|.
\]

To dokładny wzór geometryczny, ale jego wynik nadal jest liczbą zmiennoprzecinkową. **Przykład:** półokrąg z cięciwą `100` ma `L≈157.07963`. Zwiększenie wymaganej długości dwukrotnie skaluje cięciwę do `200`, przy tym samym kącie.

### 4.8. Podział łuku i jego skutki

Nowy wierzchołek to `pointAt(0.5)`, a początkowe kąty obu części są równe `θ/2`.

**Przykład G0–G0:** półokrąg `180°` dzielimy na dwa łuki `90°`. W środku ustawiamy G1, końce zewnętrzne mają G0. Każda część ma tylko jeden koniec z G1, a razem zachowują pierwotny okrąg.

**Przykład z istniejącym G1 na zewnętrznym końcu:** nie możemy dodać G1 również w nowym punkcie tej samej części. Nowy punkt dostaje G0. Druga część G0–G0 jest następnie przeliczana na półokrąg. Kształt może się zmienić; to konsekwencja przyjętej polityki topologii i reguł zadania.

### 4.9. Rysowanie łuku

Do `arcTo` przekazujemy prostokąt okręgu `(Ox-r, Oy-r, Ox+r, Oy+r)`, kąt początkowy i `sweep`. Biblioteka oczekuje stopni, więc przeliczamy `radiany * 180/π`.

**Przykład:** dla `O=(50,50)` i `r=50` prostokąt to `(0,0,100,100)`. To prostokąt opisany na całym okręgu; dopiero kąty wybierają rysowany fragment.

Łuk jest częścią zamkniętej ścieżki wielokąta, a jego zaznaczenie korzysta z tej samej geometrii. Punkt 5 nie wymagał własnego algorytmu rasteryzacji okręgów; łuki pozostają biblioteczne.

<a id="etap-5"></a>

## 5. Etap 5 — algorytmy rysowania

### 5.1. Geometria ciągła a raster

Geometria używa współrzędnych rzeczywistych; ekran ma siatkę pikseli. Rasteryzacja wybiera, które piksele mają reprezentować idealny odcinek lub krzywą.

**Przykład:** idealna prosta `y=0.4x` przechodzi przez `(1,0.4)`. Nie mamy osobnego piksela o całkowitoliczbowym adresie `(1,0.4)`; musimy wybrać sąsiedni piksel lub rozdzielić pokrycie przez antyaliasing.

`Pixel(x:Int,y:Int)` opisuje adres rastra. `Point(x:Double,y:Double)` opisuje położenie, także po przeliczeniu na ekran. Dopiero przed Bresenhamem zaokrąglamy końce do adresów pikseli.

### 5.2. Tryb biblioteczny i radio buttony

Stan interfejsu `lineAlgorithm` wybiera `Library` albo `Bresenham`. Radio buttony mają wspólną grupę wyboru i rolę dostępności `RadioButton`. Wybrany jest dokładnie jeden wariant.

**Przykład:** kliknięcie „Bresenham” powoduje rekompozycję i ponowne wyrenderowanie identycznej sceny inną metodą. Współrzędne wierzchołków, ID i relacje nie zmieniają się.

Tryb biblioteczny dla odcinków używa `drawLine`. W trybie Bresenhama piksele wyznacza nasz kod. Oba tryby używają przyrostowych próbek Béziera; różni się sposób łączenia tych próbek.

### 5.3. Bresenham — pomysł i stan początkowy

Zamiast obliczać `y=mx+b` i zaokrąglać od nowa w każdym kroku, utrzymujemy całkowitoliczbowy akumulator błędu. Na podstawie jego znaku i porównań wybieramy krok w X, Y albo obu osiach.

Nasza uogólniona wersja działa we wszystkich ośmiu oktantach, dla pionowych, poziomych i zerowych odcinków:

```text
dx = abs(x1-x0)
dy = -abs(y1-y0)        # w tej wersji dy jest niedodatnie
sx = +1 lub -1
sy = +1 lub -1
error = dx + dy
```

W pętli:

```text
narysuj (x,y)
jeśli osiągnięto koniec: przerwij
e2 = 2*error
jeśli e2 >= dy:
    error += dy
    x += sx
jeśli e2 <= dx:
    error += dx
    y += sy
```

To dwa niezależne `if`, nie `if/else`. W jednym kroku czasem zmieniają się obie współrzędne. Obie decyzje używają tej samej wartości `e2` sprzed modyfikacji.

### 5.4. Bresenham — pełny przykład liczbowy

Rysujemy od `(0,0)` do `(5,2)`:

```text
dx=5, dy=-2, sx=1, sy=1, error=3
```

| Rysowany piksel | `error` przed decyzją | `e2` | Następny ruch |
|---|---:|---:|---|
| `(0,0)` | 3 | 6 | X |
| `(1,0)` | 1 | 2 | X i Y |
| `(2,1)` | 4 | 8 | X |
| `(3,1)` | 2 | 4 | X i Y |
| `(4,2)` | 5 | 10 | X |
| `(5,2)` | 3 | — | Koniec |

Dlaczego pierwszy ruch jest tylko w X? `6>=-2` jest prawdą, ale `6<=5` fałszem. Przy drugim ruchu `2>=-2` i `2<=5` są prawdziwe, więc zmieniamy obie osie.

**Odcinek pionowy:** `dx=0`, zmienia się tylko Y. **Zerowy:** po zaznaczeniu jednego piksela od razu kończymy. **Przeciwny kierunek:** znaki kroków zmieniają się na ujemne. Przy remisach raster w odwrotnym kierunku nie musi wybierać identycznego zbioru pikseli we wszystkich konfiguracjach; nadal reprezentuje poprawny bliski odcinek.

Różnice współrzędnych i akumulator błędu używają `Long`. Konwersja do `Long` jest wykonywana przed odejmowaniem, aby `Int` nie przepełnił się już przy wyznaczaniu `dx`.

### 5.5. Jak nanosimy piksele i dlaczego przedtem skalujemy scenę

Kolejność jest istotna:

```text
współrzędne sceny -> ekran -> przycięcie -> zaokrąglenie -> Bresenham -> naniesienie pikseli
```

**Przykład:** odcinek sceny o długości `100` przy skali `2` powinien być rasteryzowany na długości `200` pikseli. Nie rysujemy najpierw 100 pikseli i nie rozciągamy gotowego obrazka dwukrotnie.

Piksel o adresie `(10,20)` ma środek `(10.5,20.5)`. Przekazujemy te centra do `drawPoints` jako kwadratowe punkty. Biblioteka nanosi wskazane punkty; nie wybiera za nas przebiegu odcinka.

**Grubość:** klasyczny Bresenham wyznacza piksele środkowej linii. Dla grubszego obrysu nanosimy w nich kwadratowy stempel o nieparzystym rozmiarze. Rozmiar `3` ma granice wyrównane do siatki, gdy środek jest na połówkach współrzędnych. To proste pogrubianie rastra; nie jest dokładnym odpowiednikiem bibliotecznego pędzla o zaokrąglonych zakończeniach.

Krawędzie mogą wyglądać schodkowo. Antyaliasing biblioteczny rozdziela pokrycie na piksele brzegowe; nasz algorytm nie implementuje antyaliasingu Wu. Nie twierdzimy też, że Bresenham w tej aplikacji zawsze będzie szybszy od biblioteki: Kotlin, alokacje punktów i przekazanie ich do Canvas mają koszt.

### 5.6. Przycinanie Lianga–Barsky’ego

Nie chcemy iterować po milionie niewidocznych pikseli krawędzi poza ekranem. Przed Bresenhamem przycinamy odcinek do prostokąta rysowania, z marginesem na szerokość pędzla.

Parametryzacja odcinka:

\[
P(t)=A+t(B-A),\qquad t\in[0,1].
\]

Każda granica prostokąta daje nierówność `p*t <= q`:

| Granica | `p` | `q` |
|---|---|---|
| lewa | `-dx` | `Ax-left` |
| prawa | `dx` | `right-Ax` |
| górna | `-dy` | `Ay-top` |
| dolna | `dy` | `bottom-Ay` |

Dla `p<0` podnosimy dolną granicę `t` do `q/p`. Dla `p>0` obniżamy górną granicę. Jeśli dolna przewyższy górną, odcinek jest niewidoczny. Dla `p=0` odcinek jest równoległy do tej granicy; przy `q<0` leży poza nią.

**Przykład:** odcinek `(-5,5)→(15,5)`, prostokąt od `(0,0)` do `(10,10)`. `dx=20`; lewa granica daje `t>=0.25`, prawa `t<=0.75`. Ostatecznie rysujemy `(0,5)→(10,5)`.

Clipping nie zmienia modelu ani długości relacji. Dotyczy wyłącznie widocznego fragmentu. Ponieważ raster rozpoczynamy od zaokrąglonego nowego końca, faza pikseli przy granicy może nie być identyczna z wycięciem gotowego, wcześniej w całości zrasteryzowanego odcinka.

### 5.7. Przejście Béziera do bazy potęgowej

Rozwijamy postać Bernsteina i grupujemy potęgi `t`:

\[
B(t)=at^3+bt^2+ct+d,
\]

\[
\begin{aligned}
a&=-P_0+3P_1-3P_2+P_3,\\
b&=3P_0-6P_1+3P_2,\\
c&=-3P_0+3P_1,\\
d&=P_0.
\end{aligned}
\]

W kodzie `a,b,c` liczymy jako kombinacje różnic punktów, więc są typu `Vector`; `d` pozostaje `Point`.

**Przykład dla naszej krzywej:**

```text
P0=(0,0), P1=(0,3), P2=(3,3), P3=(3,0)
a=(-6,0)
b=(9,-9)
c=(0,9)
d=(0,0)

x(t)=-6t³+9t²
y(t)=-9t²+9t
```

Dla `t=0.5`: `x=-0.75+2.25=1.5`, `y=-2.25+4.5=2.25`, tak samo jak z bazy Bernsteina. Zmieniliśmy bazę opisu, a nie kształt krzywej.

### 5.8. Różnice skończone — skąd wzory?

Definiujemy:

\[
\Delta B(t)=B(t+h)-B(t).
\]

Kolejna różnica to `Δ²B(t)=ΔB(t+h)-ΔB(t)`, a potem `Δ³B`. Różnica sześciennego wielomianu jest kwadratowa, następna liniowa, trzecia stała.

Po rozwinięciu:

\[
\Delta B(t)=a(3t^2h+3th^2+h^3)+b(2th+h^2)+ch.
\]

\[
\Delta^2B(t)=a(6th^2+6h^3)+2bh^2,
\qquad \Delta^3B=6ah^3.
\]

Dla początku `t=0`:

\[
\Delta B=ah^3+bh^2+ch,\quad
\Delta^2B=6ah^3+2bh^2,\quad
\Delta^3B=6ah^3.
\]

To inicjalizacja. Potem pętla używa tylko dodawania:

```text
punkt += pierwszaRóżnica
pierwszaRóżnica += drugaRóżnica
drugaRóżnica += trzeciaRóżnica
```

**Dlaczego kolejność ma znaczenie?** Punkt potrzebuje starej pierwszej różnicy, a pierwsza różnica starej drugiej. Zmiana drugiej jako pierwszej przesunęłaby wartości o jeden krok i dała błędną krzywą.

### 5.9. Różnice skończone — pełny przykład dla czterech kroków

Używamy `N=4`, `h=0.25`, `h²=0.0625`, `h³=0.015625` i wcześniejszych współczynników:

```text
początkowe ΔB  = (0.46875, 1.6875)
początkowe Δ²B = (0.5625, -1.125)
stałe Δ³B      = (-0.5625, 0)
```

| Numer próbki | `t` | Punkt |
|---|---:|---|
| 0 | 0 | `(0,0)` |
| 1 | 0.25 | `(0.46875,1.6875)` |
| 2 | 0.5 | `(1.5,2.25)` |
| 3 | 0.75 | `(2.53125,1.6875)` |
| 4 | 1 | `(3,0)` |

**Przejście z próbki 1 do 2:** pierwsza różnica po aktualizacji wynosi `(0.46875,1.6875)+(0.5625,-1.125)=(1.03125,0.5625)`. Dodajemy ją do punktu 1 i otrzymujemy `(1.5,2.25)`.

Przy większej liczbie kroków mechanizm jest identyczny. Nie obliczamy `t²` i `t³` osobno dla każdego punktu. Nie używamy też `cubicTo` do ukrytego rysowania właściwej krzywej w tle.

W arytmetyce rzeczywistej próbki są dokładnymi wartościami wielomianu. W `Double` kumulują się błędy dodawania, więc ostatnią próbkę jawnie ustawiamy na `P3`. Cała krzywa na ekranie pozostaje przybliżona łamaną między próbkami.

### 5.10. Dobór liczby kroków

Gdy okno lub skala się zwiększa, chcemy więcej próbek. Dobór opiera się na najdłuższej krawędzi wieloboku kontrolnego po przeliczeniu na ekran:

\[
M=\max(|P_1-P_0|,|P_2-P_1|,|P_3-P_2|),
\qquad N=\lceil3M\rceil.
\]

Skąd `3M`? Pochodna Béziera jest sumą trzech wektorów różnic kontrolnych z nieujemnymi wagami sumującymi się do `1`, pomnożoną przez `3`. Z nierówności trójkąta:

\[
|B'(t)|\leq3M.
\]

Dla `h=1/N` droga pomiędzy próbkami jest więc ograniczona przez `3M/N`, czyli najwyżej około jednego piksela przed zastosowaniem limitu.

**Przykład:** najdłuższy bok kontrolny ma `100` pikseli. Wybieramy `N=300`. Przy dwukrotnej skali ma `200` pikseli i używamy `N=600`.

Liczba kroków jest ograniczona do `[1,100000]`. Po dojściu do limitu nie gwarantujemy już odstępów jednopikselowych. To ograniczenie kosztu dla skrajnie dalekich uchwytów, nie adaptacyjna metoda z dokładnym zadanym błędem krzywizny.

### 5.11. Jak próbki stają się obrazem?

W trybie bibliotecznym łączymy próbki Béziera przez `lineTo` i rysujemy łamaną. W trybie Bresenhama kolejne pary próbek rasteryzujemy naszym algorytmem. Próbki są wspólne dla wypełnienia, obrysu i zaznaczenia.

**Przykład:** pomarańczowe zaznaczenie Béziera nie korzysta z osobnego `cubicTo`, więc nie powinno odstawać od właściwej krzywej narysowanej przyrostowo.

**Co jest własne:** wybór pikseli Bresenhama, konwersja bazy Béziera, różnice skończone, clipping, geometria i hit testing.

**Co jest biblioteczne:** samo nanoszenie wskazanych pikseli, tryb `drawLine`, rasteryzacja wypełnienia, łuki `arcTo`, uchwyty i przerywane linie pomocnicze. Biblioteka udostępnia powierzchnię rysowania; zadanie nie wymaga własnego sterownika ekranu.

### 5.12. Kolejność renderowania

Dla kolejnych wielokątów rysujemy wypełnienie, obrysy krawędzi, wieloboki kontrolne, zaznaczone krawędzie, wierzchołki i uchwyty. Uchwyty są na końcu wewnątrz renderowania danej figury, aby zaznaczenie nie przykryło ich obrysem.

**Przykład:** grubo zaznaczona krawędź nie zasłania własnego punktu końcowego. Później rysowana inna figura nadal może przykryć wcześniej rysowaną figurę — nie mamy globalnej osobnej warstwy wszystkich uchwytów ponad całą sceną.

<a id="etapy-6-9"></a>

## Etapy 6–9 — mysz, architektura, demonstracja i dokumentacja

### Punkt 6: jak punkt podąża za kursorem

Lewy przycisk rozpoczyna edycję; pozostałe przyciski nie rozpoczynają ruchu. Pozycję myszy najpierw przeliczamy z Canvasu do sceny. `DragGesture.begin` zapisuje cel edycji (`Vertex`, `Control` albo `Polygon`) oraz odstęp kliknięcia od punktu odniesienia. Wierzchołek i uchwyt są własnymi punktami odniesienia; dla całej figury używamy jej pierwszego wierzchołka.

Nie używamy wyłącznie różnicy między kolejnymi zdarzeniami myszy. Wyznaczamy docelową pozycję obiektu z bieżącej bezwzględnej pozycji kursora:

```text
odstęp = pozycja kliknięcia - początkowa pozycja punktu
cel = bieżący kursor - odstęp
przesunięcie = cel - aktualna pozycja punktu w modelu
```

**Przykład:** punkt jest w `(100,100)`, kliknięcie nastąpiło w `(102,103)`. Odstęp to `(2,3)`. Gdy kursor jest w `(132,113)`, cel punktu wynosi `(130,110)`. Nie ma nagłego przeskoku po kliknięciu obok środka. Jeżeli wcześniejszy ruch został odrzucony przez solver, następne zdarzenie nadal celuje w `(130,110)` i nie kumuluje brakującego przesunięcia.

`ConstraintEdit` przekazuje tożsamość przeciąganego uchwytu przez cały przebieg solvera. Przy jego wierzchołku ciągłość jest propagowana od tego uchwytu do sąsiada; solver nie przepisuje go na nowo z kierunku sąsiedniej krawędzi. Po rozwiązaniu sprawdzamy pozycję przeciąganego punktu z tolerancją `1e-5`, tak samo jak poprawność relacji. Wynik odsuwający punkt od celu nie jest zatwierdzany. Nie każda pozycja myszy ma rozwiązanie zgodne z relacjami: wtedy pozostaje poprzednia scena i pojawia się komunikat.

**Przykład G1/C1:** przeciągnięcie pierwszego uchwytu Béziera zmienia styczną na początku. Końce tego Béziera pozostają w miejscu; przestawia się sąsiedni uchwyt albo dalszy koniec sąsiedniego odcinka. Przeciąganie drugiego uchwytu działa analogicznie na drugim końcu.

**Wyjątek z długością:** jeśli krzywa ma zadaną długość, ruch uchwytu może wymagać przesunięcia jej własnych końców. Skalujemy pozostałe punkty wokół przeciąganego uchwytu: `X' = H + (X-H)*s`, gdzie `H` to uchwyt i `s = długość docelowa / bieżąca`. Wtedy `H'=H`, a przy stałym kształcie względnym wszystkie długości skalują się przez `s`. Następne relacje nadal muszą przejść walidację.

**Tworzenie:** „Nowy wielokąt” uruchamia szkic. Kolejne lewe kliknięcia dodają punkty; Enter, kliknięcie pierwszego punktu lub przycisk zamykają kontur. Backspace usuwa ostatni punkt, Escape anuluje szkic. `Scene.addPolygon` wymaga co najmniej trzech skończonych punktów i różnych sąsiadów, przydziela nowe ID, zachowuje istniejące figury i relacje. Samoprzecięcia są dopuszczalne; nie wymagamy dodatniego pola algebraicznego.

### Punkt 7: granice odpowiedzialności

Geometria i ograniczenia pozostają w `model/`, niezależne od Compose. Funkcje Bresenhama, różnic skończonych i clippingu pozostają czystymi algorytmami. Renderer jedynie rysuje ich wynik. `DragGesture` przekłada pozycję kursora na żądanie zmiany modelu. `Main.kt` rozpoznaje element i przechowuje stan wyboru, szkic, dialogi i komunikaty; `HelpDialog` wyświetla pomoc. Nie wprowadzamy dużego frameworka architektonicznego dla samej liczby klas.

**Przykład:** jednostkowy test może przesunąć uchwyt pod C1 bez uruchamiania okna. Może również zasymulować bezwzględne pozycje myszy i sprawdzić odstęp kliknięcia. Same zdarzenia przycisków i skrótów wymagają dodatkowej kontroli w działającym oknie.

### Punkt 8: scena gotowa do prezentacji

Scena zawiera samoprzecinającą się gwiazdę, pięciokąt z Bézierem oraz czworokąt z półokręgiem. Górny odcinek czworokąta biegnie od `(650,150)` do `(810,150)`, więc ma długość `160`. Prawy biegnie pionowo do `(810,315)`, więc jest prostopadły do górnego. Relacje są zapisane w scenie od uruchomienia i już spełnione: start nie wymaga odkształcenia figur.

**Jak dodać figurę w kodzie:** dopisać kolejne wywołanie `polygon("Nazwa", listOf(...))` do listy w `DemoScene.kt`. Pomocnik nadaje ID figurze, wierzchołkom i krawędziom. Parametr `withConstraints=false` pozwala izolować testy pojedynczych operacji od demonstracyjnych relacji startowych.

### Punkt 9: dokumentacja pod ręką

Przycisk „Pomoc · F1” otwiera przewijaną instrukcję z zasobu `src/main/resources/help.md`. Zasób jest dołączony do aplikacji, więc pomoc działa offline i po zainstalowaniu pakietu. Obejmuje mysz i skróty, tworzenie, założenia, model danych, algorytmy rysowania i algorytm relacji, wraz z ograniczeniami solvera. Tekst można zaznaczać i kopiować.

**Przykład prezentacji:** uruchom program, pokaż relacje na czworokącie, przeciągnij uchwyt pięciokąta, utwórz trójkąt z trzech kliknięć, naciśnij Enter, a następnie F1. Ten dokument jest szerszym materiałem do nauki; pomoc aplikacji służy użytkownikowi podczas pracy.

<a id="weryfikacja"></a>

## 6. Testy, złożoność i ograniczenia

### 6.1. Jakie dowody poprawności mamy?

Ostatni zapisany raport obejmuje **57 testów**: 17 testów sceny, 14 łuków, 10 interakcyjnej edycji prostopadłości, 7 interakcji/tworzenia/sceny startowej, 6 algorytmów rysowania, 2 renderera i 1 układu pomocy. To liczba metod testowych; jedna metoda Bresenhama sprawdza wiele setek przypadków w pętli.

| Obszar | Co jest sprawdzane | Przykład |
|---|---|---|
| Topologia | Podział, usuwanie, ID, minimum trzech punktów | Usunięcie wierzchołka czworokąta daje trójkąt |
| Przesuwanie | Zmiana właściwych elementów | Translacja figury nie porusza innych bez relacji |
| Relacje | Długość, prostopadłość, czyszczenie relacji | Długość i prostopadłość sąsiednich krawędzi |
| Równoważność edycji | Oba końce obu krawędzi, kolejność wyboru, priorytety i długości | Przeciąganie ostatniej krawędzi łańcucha zmienia poprzednie |
| Stabilność gwiazdy | Ochrona kotwicy źródła i powtarzane przeciąganie przy `#11 ⟂ #8`, `#8 ⟂ #10` | Dla każdego wierzchołka 100 ruchów tam i z powrotem; potem sztywna translacja figury |
| Ciągłość | G1/C1 i reakcja na uchwyt | Przeciągnięty uchwyt C1 zmienia sąsiedni odcinek |
| Łuki | Promień, punkty, G1 i degeneracje | Po wyłączeniu ostatniego G1 powraca półokrąg |
| Bresenham | Wszystkie oktanty, ciągłość, końce i odchylenie | Kierunki `dx,dy` od `-15` do `15`, oba zwroty |
| Bézier przyrostowy | Porównanie z niezależnym wzorem Bernsteina | 1, 2, 7, 256 i 8192 kroki, również pętle |
| Faktyczne piksele | Odczyt obrazu Canvas | Pomalowane są dokładnie wskazane piksele cienkiej linii |
| Cała scena | Oba tryby i środek gwiazdy | Środkowy piksel gwiazdy nie jest biały |
| Pomoc | Rzeczywisty obraz dialogu i przewijanie w dwóch rozmiarach | Nagłówek i stopka pozostają widoczne, a treść zmienia się po przewinięciu |

**Dlaczego niezależny wzór?** Test porównujący funkcję z jej własnym wynikiem nie wykryłby tej samej pomyłki po obu stronach. Przyrostowy Bézier jest sprawdzany względem bezpośredniego wzoru Bernsteina. Przy Bresenhamie testy dodatkowo sprawdzają własności geometryczne.

Testy nie dowodzą poprawności dla każdego możliwego układu ograniczeń. Przykładowo wszystkie 57 testów nie wykluczają problemów z bardzo gęstą, cykliczną siecią relacji. Pomoc jest dodatkowo renderowana w ImageComposeScene i sprawdzana przy przewijaniu; nie zastępuje to ręcznego sprawdzenia wszystkich kliknięć i skrótów w natywnym oknie.

### 6.2. Złożoność — teoria a nasz kod

Niech `V` oznacza liczbę wierzchołków, `E` krawędzi, `C` relacji, `J` aktywnych ustawień ciągłości, `A` łuków, `N` próbek Béziera i `P` wybranych pikseli.

| Operacja | Koszt podstawowego algorytmu | Uwaga o implementacji |
|---|---|---|
| Długość wektora, iloczyn skalarny | `O(1)` | Stała liczba działań |
| Odległość punkt–odcinek | `O(1)` | Rzut i clamp |
| Winding number | `O(E)` dla odcinków | Krzywe mają po 40 fragmentów |
| De Casteljau stopnia 3 | `O(1)` | Stałe sześć interpolacji dla jednego podziału |
| Długość Béziera z N próbek | `O(N)` | W modelu N=40 |
| Bresenham | `O(max(|dx|,|dy|)+1)` | Sam akumulator ma `O(1)` pamięci; renderer zbiera piksele |
| Bézier przyrostowy | `O(N)` czasu i pamięci | Zwracamy listę N+1 punktów |
| Liang–Barsky | `O(1)` | Cztery granice prostokąta |
| Wyszukanie krawędzi przez ID | `O(E)` | W obecnym kodzie skan list, nie mapa |

Nie należy twierdzić, że cała edycja relacji ma `O(1)`, tylko dlatego że wzór korekty jest krótki. `edgeGeometry` przeszukuje scenę, a tworzenie nowej sceny kopiuje listy i sprawdza inwarianty.

Jedno przejście solvera to w przybliżeniu koszt wielu skanów/kopii `O((C+J+A)*(V+E))`; wykonujemy 16 przebiegów. Końcowa walidacja i renderer również wielokrotnie wyszukują krawędzie. W szczególności walidacja łuków przegląda krawędzie i pobiera ich geometrię przez kolejne wyszukiwania, co może dawać koszt kwadratowy względem wielkości sceny.

**Przykład usprawnienia:** mapa `edgeId -> polygon/index` zmniejszyłaby wielokrotne wyszukiwania, a indeks zależności pozwoliłby aktualizować tylko relacje związane z przeciąganym punktem. W tej wersji czytelność i mała scena były ważniejsze od pełnej optymalizacji CAD.

### 6.3. Ograniczenia, które warto uczciwie powiedzieć

1. Solver ma ustaloną liczbę sekwencyjnych korekt. Nie gwarantuje rozwiązania każdej zgodnej sieci relacji ani minimalnego przesunięcia. Dla pojedynczej pary odwrócenie kolejności kliknięcia nie ustala innej krawędzi sterującej; przy wielu relacjach wynik nadal może zależeć od kolejności wykonywania korekt.
2. Solver może wewnętrznie przesuwać punkty podczas kolejnych korekt. Przed zatwierdzeniem sprawdza pozycję przeciąganego punktu i odrzuca wynik, jeśli odsunął go od celu. Nie jest to globalny solver z dokładną analizą stopni swobody.
3. Długość Béziera, hit testing i wnętrze krzywych korzystają z 40 odcinków. To przybliżenie bez adaptacyjnej kontroli błędu. Próbki rysowania są inne, więc blisko brzegu krzywej może wystąpić mała rozbieżność między widocznym obrysem a trafieniem.
4. Podział krzywej zachowuje kształt przed wymuszeniem zewnętrznych relacji. Podział łuku z zewnętrznym G1 może z definicji zmienić kształt części G0–G0.
5. Zmiana typu krawędzi jest zmianą reprezentacji i geometrii; nie jest ogólną dokładną konwersją okręgu w pojedynczy wielomianowy Bézier.
6. G1/G0 łuków ma politykę z zadania. Nie ma C1 dla łuku, dowolnego promienia w trybie G0–G0 ani przycisku odwrócenia strony półokręgu.
7. Bresenham nie ma własnego antyaliasingu. Pogrubianie kwadratowym stemplem różni się od bibliotecznego obrysu z okrągłymi zakończeniami.
8. Dla skrajnych współrzędnych mogą ujawnić się problemy precyzji lub wydajności. Są zabezpieczenia przed zerem i limit próbek, ale nie pełny system odpornej arytmetyki geometrycznej.
9. Nie ma zapisu, wczytywania, undo/redo ani usuwania całych wielokątów przez użytkownika. Tworzenie przez klikanie jest dostępne.
10. Projekt przewiduje dystrybucje macOS/Windows, ale nie należy deklarować, że działanie na Windows zostało zweryfikowane w tej sesji. Konfiguracja targetów i faktyczne testy na danym systemie to różne rzeczy.

**Przykład odpowiedzi zamiast ukrywania ograniczenia:** „Przy bardzo rozbudowanych cyklach relacji mój solver może nie osiągnąć zbieżności. Operacja jest wtedy odrzucana i zachowuję poprzednią poprawną scenę. Usprawnieniem byłby globalny solver z analizą stopni swobody i sterowaniem kotwicami”.

### 6.4. Uruchamianie i budowanie

Potrzebny jest JDK 21. Gradle Wrapper pobiera/wybiera wersję Gradle z konfiguracji projektu, dlatego nie trzeba utrzymywać osobnej zgodnej instalacji Gradle.

macOS, w katalogu projektu:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
./gradlew run
./gradlew test
```

Windows:

```bat
gradlew.bat run
gradlew.bat test
```

Pakiet budujemy przez `packageDistributionForCurrentOS`. Konfiguracja przewiduje DMG, EXE i MSI; odpowiednią dystrybucję buduje się na danym systemie. Sam wpis `TargetFormat.Exe` na macOS nie oznacza, że zweryfikowaliśmy program Windows.

`settings.gradle.kts` określa repozytoria i nazwę projektu. `build.gradle.kts` określa pluginy, zależności, JDK, testy i pakowanie. `gradle.properties` m.in. ustawia pamięć procesu Gradle i kodowanie UTF-8.

**Przykład problemu środowiskowego:** uruchomienie domyślnym JDK innym niż wymagany może powodować kłopoty z narzędziami. Najpierw sprawdzamy `JAVA_HOME` i toolchain, a nie zmieniamy matematyki programu.

<a id="pytania"></a>

## 7. Pytania prowadzącego i odpowiedzi, które trzeba rozumieć

### 7.1. Projekt i model

**1. Dlaczego Kotlin i Compose?**

Pozwalają zbudować desktopowy interfejs na JVM, a Canvas i obsługa wskaźnika wystarczają do edytora 2D. Własną geometrię trzymam poza UI, więc biblioteka nie rozwiązuje za mnie relacji ani algorytmu Bresenhama. Kod Kotlin/JVM jest wspólny dla docelowych systemów, choć dystrybucje trzeba zbudować i sprawdzić osobno.

**2. Czy to MVC lub MVVM?**

Jest rozdzielenie modelu, rysowania i interakcji, ale nie wdrożyliśmy formalnie pełnego MVVM z osobną klasą ViewModel. Stan interakcji znajduje się w funkcji Compose, a logika geometrii w modelu. Lepiej opisać faktyczny podział niż przypisywać projektowi wzorzec, którego klas nie ma.

**3. Dlaczego w krawędzi nie zapisano osobnych kopii końców?**

Końce wynikają z kolejnych wierzchołków wielokąta. Dzięki temu przesunięcie wspólnego wierzchołka automatycznie zmienia obie sąsiednie krawędzie. `EdgeGeometry` wylicza ich aktualny opis.

**4. Dlaczego punkt i wektor są osobnymi typami?**

Położenie i przesunięcie mają inne dozwolone działania. Kompilator nie pozwala przypadkowo potraktować sumy dwóch położeń jako przesunięcia. Przykładowo `B-A` daje wektor, a `A+(B-A)*0.5` punkt środkowy.

**5. Dlaczego nie modyfikujesz współrzędnej przez `vertex.point.x = ...`?**

Stosujemy niezmienny styl aktualizacji z `val` i `copy`. Powstaje kandydatura nowej sceny, a Compose dostaje ją dopiero po powodzeniu operacji. Łatwiej w ten sposób odrzucić zmianę i testować brak wpływu na inne elementy.

**6. Czy ID i indeks to to samo?**

Nie. Indeks opisuje pozycję na liście, a ID to tożsamość elementu. Wstawienie punktu przesuwa indeksy, ale relacja przypięta do ID nadal wskazuje ten sam obiekt geometryczny, dopóki topologia nie wymaga jej unieważnienia.

### 7.2. Edycja i wybór

**7. Jak rozpoznajesz kliknięcie w krawędź?**

Mierzę odległość punktu od odcinka przez rzut i clamp. Dla krzywej przybliżam ją 40 odcinkami i wybieram najmniejszą odległość. Tolerancję przeliczam z pikseli na jednostki sceny.

**8. Po co clamp parametru rzutu?**

Bez niego szukałbym punktu na całej prostej. Kliknięcie za końcem odcinka mogłoby zostać uznane za bardzo bliskie krawędzi, mimo że leży daleko od obu jej końców.

**9. Jak działa wybór figury w jej wnętrzu?**

Liczymy niezerową liczbę nawinięć konturu wokół punktu. Kierunek przecięcia i wyznacznik określają podpisany wkład. Krzywe przed tym testem przybliżamy fragmentami.

**10. Dlaczego środek gwiazdy jest wypełniony?**

Ma niezerową liczbę nawinięć, w tym przypadku o wartości bezwzględnej 2. Reguła parzystości dałaby dziurę. Aktualnie zarówno renderer, jak i hit testing korzystają z non-zero winding.

**11. Czy samoprzecięcia wymagają policzenia wszystkich punktów przecięcia krawędzi?**

Nie dla obecnych funkcji. Kontur może sam się przecinać, a wnętrze definiujemy regułą wypełnienia. Jawna lista przecięć byłaby potrzebna np. przy pewnych operacjach boolowskich lub rozbijaniu figury na proste obszary; tego nie implementujemy.

**12. Dlaczego po kliknięciu krawędzi przeciąga się cała figura?**

Taki jest wybrany schemat obsługi. Wierzchołek i uchwyt zmieniają kształt, a krawędź i wnętrze przesuwają cały wielokąt. Samo kliknięcie krawędzi dodatkowo służy do wybrania jej dla relacji.

### 7.3. Relacje i solver

**13. Jak wymuszasz długość?**

Normalizuję kierunek i mnożę przez docelową długość, pozostawiając punkt kotwiczący. Kotwicę wybieram z uwzględnieniem przeciąganego wierzchołka i priorytetów propagacji: jeśli użytkownik przeciąga koniec, mogę poprawić początek. Dla krzywych używam ich długości i skaluję odpowiednią geometrię. Po tej korekcie sprawdzam pozostałe relacje.

**14. Jak sprawdzasz prostopadłość?**

Iloczyn skalarny jednostkowych kierunków ma być bliski zeru. Wymuszenie polega na ustawieniu korygowanej krawędzi w kierunku prostopadłym do krawędzi odniesienia, z dobraniem bliższego zwrotu.

**15. Dlaczego wybierasz wolny koniec przy sąsiednich krawędziach?**

Przesunięcie wspólnego punktu zmieniłoby również geometrię odniesienia. Wcześniej powodowało to walkę korekt długości i prostopadłości. Pozostawienie wspólnego punktu usuwa ten konkretny problem.

**16. Czy pierwsza i druga krawędź są traktowane symetrycznie?**

Obie można edytować i obracać. Przy aktualnym przeciąganiu krawędź bliższa źródłu edycji steruje relacją, a partner się dostosowuje. Priorytety propagujemy przez graf relacji algorytmem BFS. W remisie, np. bez aktywnej interakcji, używamy stabilnych ID, więc odwrócenie kolejności kliknięcia pary daje tę samą geometrię. Sama korekta nadal ma w danym momencie kierunek: nie obracamy automatycznie obu krawędzi po połowie i nie rozwiązujemy globalnego minimum przesunięć.

**17. Dlaczego solver nie robi jednej korekty?**

Każda relacja może współdzielić punkty z inną. Poprawienie jednej może popsuć drugą. Powtarzamy korekty i na końcu walidujemy cały układ.

**18. Czy 16 przebiegów dowodzi sprzeczności?**

Nie. Dowodzi tylko, że nasz algorytm nie uzyskał sceny spełniającej tolerancję w tym przebiegu. To ograniczenie metody. Zachowuję poprzednią scenę i pokazuję odmowę.

**19. Dlaczego nie porównujesz `double == double`?**

Wyniki dzielenia, normalizacji i funkcji trygonometrycznych mają błędy zaokrągleń. Sprawdzam błąd względem tolerancji. Osobny epsilon służy do wykrywania zera przed wyznaczaniem kierunku.

**20. Co oznacza prostopadłość Béziera i łuku?**

W tej aplikacji prostopadłość dotyczy ich cięciw. Ciągłość na połączeniu jest odrębnym warunkiem opartym na stycznych.

**21. Dlaczego po usunięciu relacji figura nie wraca do dawnego kształtu?**

Relacja określa warunek aktualnej i przyszłej edycji. Jej usunięcie nie jest operacją undo i nie mamy zapisanej historii poprzedniej geometrii.

### 7.4. Bézier i ciągłość

**22. Czy krzywa przechodzi przez wszystkie cztery punkty?**

Przechodzi przez pierwszy i ostatni. Dwa środkowe punkty kontrolują kształt i styczne; zwykle nie leżą na krzywej.

**23. Skąd wiadomo, jaka jest styczna na początku?**

Z pochodnej: `B'(0)=3(P1-P0)`. Kierunek jest więc zgodny z pierwszym bokiem wieloboku kontrolnego. Na końcu `B'(1)=3(P3-P2)`.

**24. Czy współliniowe uchwyty zawsze oznaczają G1?**

Nie wystarczy sama współliniowość. Wektory przejścia przez wspólny punkt muszą mieć zgodny zwrot, a nie tworzyć zawrócenie. Wymagamy także niezerowych stycznych.

**25. Czym różni się G1 od C1?**

G1 równa kierunki jednostkowe. C1 równa całe pochodne względem użytej parametryzacji. Uchwyty o długościach 2 i 5 mogą dawać G1, lecz nie C1.

**26. Dlaczego przy połączeniu odcinka i Béziera występuje jedna trzecia?**

Pochodna Béziera to trzy razy wektor uchwytu. Odcinek sparametryzowany na `[0,1]` ma pochodną równą całemu wektorowi odcinka. Zrównanie ich wymaga, aby uchwyt był jedną trzecią tego wektora.

**27. Czy C1 gwarantuje tę samą krzywiznę po obu stronach?**

Nie. Gwarantuje zgodność pierwszych pochodnych. Druga pochodna, a więc zachowanie krzywizny, może się zmienić. Nie implementujemy C2 ani G2.

**28. Dlaczego `t=0.5` nie oznacza połowy długości Béziera?**

Parametr nie jest automatycznie długością łuku. Szybkość `|B'(t)|` zwykle się zmienia. Do znalezienia połowy długości trzeba byłoby rozwiązać zadanie na długości łuku, np. przez numeryczne całkowanie i wyszukiwanie parametru.

**29. Dlaczego de Casteljau?**

Przez kolejne interpolacje daje dokładny geometrycznie podział na dwa segmenty tego samego przebiegu. Nie polega na zgadywaniu nowych uchwytów. Dla `t=0.5` wszystkie interpolacje są środkami punktów.

**30. Dlaczego długość Béziera jest przybliżona?**

Sumujemy długości 40 cięciw. To prosty algorytm numeryczny; nie wyznaczamy dokładnej całki. Przy bardzo dużej krzywiźnie lub długich uchwytach potrzebna byłaby lepsza kontrola błędu.

### 7.5. Łuki

**31. Dlaczego dwa punkty nie wyznaczają okręgu?**

Środek może znajdować się w wielu miejscach na symetralnej odcinka między punktami. Potrzebny jest dodatkowy warunek. U nas G0–G0 narzuca półokrąg, a G1 narzuca styczną na jednym końcu.

**32. Dlaczego środek leży na symetralnej?**

Musi mieć taką samą odległość od obu końców, równą promieniowi. Zbiór punktów o równych odległościach od A i B jest symetralną odcinka AB.

**33. Co to jest kąt środkowy i dlaczego znak ma znaczenie?**

To kąt obrotu między promieniami do końców łuku. Znak opisuje kierunek przejścia, a wielkość rozróżnia krótki i długi łuk. `sweep=π` daje półokrąg.

**34. Dlaczego długość to `r*theta`, a nie `2πr*theta`?**

Kąt jest w radianach. Pełny obrót ma `2π`, więc `r*2π` daje obwód. Gdyby kąt był w stopniach, należałoby użyć `2πr*(stopnie/360)`.

**35. Dlaczego `atan2`, a nie zwykły `atan` albo samo `acos`?**

`atan2` korzysta z wyznacznika i iloczynu skalarnego, zachowuje znak kąta i informację o ćwiartce. Nie wymaga dzielenia przez składową mogącą być zerem. `acos` daje samą wielkość kąta bez zwrotu.

**36. Czy brak G1 na obu końcach to ograniczenie matematyki?**

To wymóg zadania. Dwie dowolne styczne mogą być sprzeczne, lecz szczególne zgodne pary istnieją. Nasza aplikacja blokuje drugi G1 także wtedy.

**37. Co się dzieje ze styczną równoległą do cięciwy?**

Nie wyznacza skończonego koła przez dwa różne końce. Kąt zbliża się do degeneracji, a promień do nieskończoności. Odrzucamy zmianę zamiast produkować NaN lub nieskończoność.

### 7.6. Rysowanie

**38. Czy użycie `drawPoints` oznacza, że Bresenham jest biblioteczny?**

Nie. Nasz kod decyduje o współrzędnych pikseli; biblioteka je nanosi. Nie wywołujemy w tym trybie bibliotecznego rysowania odcinka w tle. Samo udostępnienie bufora lub Canvas jest potrzebne także własnemu algorytmowi.

**39. Co robi zmienna błędu Bresenhama?**

Przechowuje stan decyzyjny opisujący odchylenie rastra od idealnego przebiegu. Pozwala wybrać następny krok przez dodawanie i porównania całkowitoliczbowe. Jej konkretna postać zależy od wariantu algorytmu; w naszym `dy` jest ujemne.

**40. Dlaczego dwa niezależne `if`?**

W jednym kroku trzeba czasem przesunąć się po przekątnej. `if/else` pozwalałoby zmienić tylko jedną oś i nie byłoby tym samym algorytmem. Oba porównania muszą używać starego `e2`.

**41. Dlaczego najpierw przechodzisz na ekran?**

Bresenham wybiera piksele końcowego rastra. Skalowanie dopiero po wybraniu pikseli rozciągałoby raster zamiast rasteryzować rzeczywistą linię w nowej skali. Podobnie liczba próbek Béziera zależy od wielkości ekranowej.

**42. Dlaczego potrzebujesz clippingu, skoro Canvas i tak przytnie obraz?**

Canvas może odrzucić niewidoczne fragmenty przy nanoszeniu, ale bez clippingu nasza pętla Bresenhama nadal wygenerowałaby ich piksele. Przycinam wcześniej, aby uniknąć kosztu niewidocznej geometrii.

**43. Dlaczego przepisujesz Béziera do bazy potęgowej?**

W tej bazie łatwo wyliczyć początkowe różnice skończone. To ten sam wielomian i ta sama krzywa, ale wygodniejszy zapis do szybkiej iteracji.

**44. Dlaczego trzecia różnica jest stała?**

Każde różnicowanie skończone przy stałym kroku obniża stopień wielomianu o jeden. Dla stopnia 3 kolejne stopnie to 2, 1 i 0. Czwartej różnicy nie trzeba przechowywać, bo jest zerowa.

**45. Czy w pętli rysowania Béziera są mnożenia?**

W pętli generowania punktów aktualizujemy punkt i różnice przez dodawanie. Mnożenia i dzielenie są w inicjalizacji. Osobne koszty, takie jak nanoszenie próbek, mogą oczywiście wykonywać inne operacje.

**46. Czy wszystkie punkty przyrostowego algorytmu są dokładne?**

W idealnej arytmetyce to dokładne wartości wielomianu dla kolejnych parametrów. W `Double` występują błędy i ich kumulacja. Dodatkowo samo łączenie próbek jest przybliżeniem gładkiej krzywej. Jawnie ustawiamy ostatni punkt na dokładny koniec.

**47. Dlaczego liczby kroków nie wyznaczasz z odległości końców?**

Bézier może tworzyć pętlę, mieć zbieżne końce i nadal duży rozmiar. Najdłuższa krawędź wieloboku kontrolnego uwzględnia uchwyty. Ograniczenie prędkości daje sensowniejszą liczbę próbek na ekranie.

**48. Czy biblioteka nadal jest używana do wypełnień i okręgów?**

Tak. Wymaganie własnej implementacji dotyczy wyboru pikseli odcinków i przyrostowego generowania punktów Béziera. Wypełnienie, łuki, uchwyty i linie pomocnicze korzystają z biblioteki.

### 7.7. Testy i rozwój

**49. Jak wiesz, że radio button naprawdę zmienia algorytm?**

W teście renderujemy tę samą scenę w obu trybach i porównujemy rzeczywiste obrazy. Wynik ma różne piksele. Osobny test cienkiej linii sprawdza dokładnie, które piksele mają pokrycie, a które są przezroczyste.

**50. Co poprawiłbyś jako następne?**

Największe obszary to solver: lepsza analiza zależności, skuteczniejsze uwzględnianie kotwic wewnątrz wszystkich korekt i wykrywanie rzeczywistych konfliktów; ponadto adaptacyjna długość i hit testing krzywych, mapa ID dla szybkiego dostępu, undo/redo i zapis sceny. Pozycja przeciąganego punktu jest już sprawdzana przy zatwierdzeniu. Każde dalsze usprawnienie powinno mieć osobne testy zachowania.

<a id="prezentacja"></a>

## 8. Scenariusz prezentacji i karta wzorów

### 8.1. Wypowiedź wprowadzająca — około minuty

„Projekt jest edytorem wielokątów i konturów krzywoliniowych. Model sceny jest oddzielony od interfejsu Compose i algorytmów rysowania. Figury mają stabilne identyfikatory wierzchołków i krawędzi. Edycja uwzględnia relacje długości, prostopadłości i ciągłości. Krawędź może być odcinkiem, sześciennym Bézierem albo łukiem okręgu. Dla odcinków można wybrać algorytm biblioteczny lub własny Bresenham. Béziery są próbkowane przyrostowo po przejściu do bazy potęgowej. Operacje geometrii są sprawdzane przed przyjęciem nowej sceny.”

### 8.2. Kolejność demonstracji

1. **Scena startowa.** Wskaż samoprzecinającą się gwiazdę, Béziera w pięciokącie i półokrąg na czworokącie. Wyjaśnij, że to różne typy krawędzi w jednym modelu.
2. **Punkt i figura.** Przeciągnij wierzchołek, potem wnętrze figury. Powiedz, że pierwszy ruch zmienia pojedyncze położenie, drugi dodaje wspólną deltę.
3. **Gwiazda.** Kliknij środek i przesuń ją. Wyjaśnij non-zero winding i różnicę względem parzystości.
4. **Topologia.** Na prostej krawędzi dodaj punkt, potem go usuń. Pokaż stabilność ID pozostałych krawędzi i minimum trzech wierzchołków.
5. **Długość.** Wybierz jedną krawędź. Zwróć uwagę na aktualną długość w dialogu, wpisz inną, przeciągnij punkt i pokaż zachowanie relacji.
6. **Prostopadłość.** Wyczyść wybór krawędzi, zaznacz dwie — mogą należeć do różnych figur. Ustaw relację. Obróć obie przez przeciąganie ich wolnych końców. Wyjaśnij, że źródło edycji wybiera tymczasowe odniesienie, a nie kolejność kliknięcia. Przy sąsiednich krawędziach pokaż zachowanie wspólnego wierzchołka podczas korekty kąta oraz możliwość osobnego przeciągnięcia samego wspólnego punktu.
7. **Bézier.** Przywróć scenę, aby uniknąć przypadkowej gęstej sieci relacji. Wybierz krzywą i jej uchwyt. Pokaż trzy boki wieloboku kontrolnego. Dla G0 uchwyt porusza się niezależnie.
8. **G1/C1.** Na dogodnym połączeniu odcinka i Béziera pokaż różnicę: G1 zachowuje kierunek, a C1 także wielkość pochodnej. Wyjaśnij czynnik 3. Potem pokaż podział krzywej.
9. **Łuk.** Przywróć scenę. Pokaż promień półokręgu, ustaw G1 na jednym końcu i przesuń sąsiedni punkt. Pokaż wyłączony drugi G1. Wróć na obu końcach do G0, co przywraca półokrąg.
10. **Rysowanie.** Przełącz radio button biblioteczny/Bresenham. Wskaż schodki pikseli. Powiedz, że punkty Béziera w obu trybach pochodzą z różnic skończonych, a nie z `cubicTo`.
11. **Testy i ograniczenia.** Pokaż testy i przykład, w którym operacja zostaje odrzucona. Wyjaśnij, że odmowa nie zostawia połowicznie zmienionej sceny i nie stanowi uniwersalnego dowodu sprzeczności.

Przećwicz tę kolejność przed oddaniem. Złożone relacje prezentuj na kontrolowanych, prostych konfiguracjach; gdy prowadzący poda inną, analizuj faktyczne zachowanie i ograniczenia solvera.

### 8.3. Mapa funkcji do szybkiego znalezienia w IDE

| Funkcja lub typ | Gdzie | Co umieć pokazać |
|---|---|---|
| `Point`, `Vector` | `Geometry.kt` | Semantykę operatorów i brak dodawania położeń |
| `addPolygon` | `Scene.kt` | Minimum trzech punktów, niezerowe boki, nowe ID bez zmiany starych figur |
| `DragTarget`, `DragGesture` | `interaction/DragGesture.kt` | Bezwzględny cel ruchu oraz odstęp kliknięcia |
| `HelpDialog` | `ui/HelpDialog.kt` | Instrukcję z zasobu dołączonego do aplikacji |
| `Polygon`, `Scene`, `EdgeGeometry` | `Polygon.kt`, `Scene.kt`, `EdgeGeometry.kt` | Listy cykliczne, inwarianty, stabilne ID |
| `tryMoveVertex`, `tryMovePolygon` | `Scene.kt` | Kopię kandydatury i uruchomienie solvera |
| `insertAtEdgeMidpoint`, `removeVertex` | `Scene.kt` | Aktualizację topologii i usunięcie relacji |
| `addLengthConstraint`, `enforceLength` | `Scene.kt`, `ConstraintSolver.kt` | Rejestrację warunku i korektę geometrii |
| `addPerpendicularConstraint`, `enforcePerpendicular` | `Scene.kt`, `ConstraintSolver.kt` | Iloczyn skalarny i wolny koniec |
| `solveConstraints`, `constraintsSatisfied` | `ConstraintSolver.kt` | 16 korekt i osobną walidację |
| `ConstraintEdit`, `perpendicularPriorities`, `keepEndForCorrection` | `ConstraintSolver.kt` | Jedno źródło na grupę relacji, priorytety BFS i wybór kotwicy korekt długości oraz prostopadłości |
| `toggleBezier`, `setContinuity` | `Scene.kt` | Typ geometrii, flagę `explicit`, klasy ciągłości |
| `propagateDraggedControl`, `enforceContinuityAt` | `ConstraintSolver.kt` | Kto jest źródłem stycznej, kto się dostosowuje |
| `splitBezier` | `BezierEditing.kt` | Interpolacje de Casteljau potrzebne do podziału |
| `pointAt`, `arcLength` | `EdgeGeometry.kt` | Wzór parametryczny i 40 cięciw |
| `toggleArc`, `enforceArcs`, `arcContinuityError` | `Scene.kt`, `ConstraintSolver.kt` | G0–G0 i dopasowanie G1, blokadę dwóch G1 |
| `distanceToSegment`, `containsPoint` | `HitTesting.kt` | Rzut z clampem i winding number |
| `CircularArcGeometry` | `CircularArc.kt` | Środek, promień, obrót i styczne |
| `fromTangent` | `CircularArc.kt` | Podwojony podpisany kąt i degeneracje |
| `bresenhamLine` | `DrawingAlgorithms.kt` | Błąd całkowitoliczbowy, dwa `if`, `Long` |
| `CubicPowerBasis`, `incrementalBezier` | `DrawingAlgorithms.kt` | Współczynniki i trzy różnice |
| `bezierStepCount`, `clipLine` | `DrawingAlgorithms.kt` | Dobór kroku ekranowego i Liang–Barsky |
| `Viewport` | `SceneRenderer.kt` | Transformację ekran–scena i scena–ekran |
| `drawPolygonScene`, `drawEdge` | `SceneRenderer.kt` | Rozdzielenie wypełnienia i obrysu, wybór trybu |
| `drawBresenhamPolyline` | `SceneRenderer.kt` | Nanoszenie wybranych pikseli i clipping |
| `hitTest`, `PolygonEditor` | `Main.kt` | Priorytet wyboru, stan Compose i radio buttony |

### 8.4. Karta wzorów do powtórki

| Zagadnienie | Wzór lub zasada | Mikroprzykład |
|---|---|---|
| Odległość | `sqrt(dx²+dy²)` | `(3,4)` ma długość 5 |
| Normalizacja | `v/|v|` | `(3,4) -> (0.6,0.8)` |
| Prostopadły wektor | `(x,y) -> (-y,x)` | `(3,4) -> (-4,3)` |
| Iloczyn skalarny | `ux*vx + uy*vy` | Dla powyższej pary 0 |
| Wyznacznik | `ux*vy - uy*vx` | Dla `(1,0),(0,1)` jest 1 |
| Środek odcinka | `A+(B-A)/2` | `(0,0),(10,4) -> (5,2)` |
| Rzut na odcinek | `t=clamp(dot(Q-A,d)/dot(d,d),0,1)` | `(4,3)` na poziomej krawędzi daje `(4,0)` |
| Długość zadana | `B'=A+normalize(B-A)*L` | `(3,4)` do długości 10 daje `(6,8)` |
| Bézier | `Σ Bernstein_i(t)*Pi` | Wagi w połowie: `1,3,3,1` podzielone przez 8 |
| Pochodne Béziera | `3(P1-P0)`, `3(P3-P2)` | Uchwyt długości 4 daje pochodną długości 12 |
| G1 | zgodne jednostkowe styczne | Uchwyty 2 i 5 mogą spełniać G1 |
| C1 dwóch kubik | `P-Cin = Cout-P` | Uchwyty lustrzane |
| Linia–Bézier C1 | `Cout-P = (P-A)/3` | Linia 12 -> uchwyt 4 |
| Środek okręgu | `M + d⊥/(2*tan(theta/2))` | Cięciwa 100 i 90° -> środek `(50,50)` |
| Długość łuku | `r*abs(theta)` | Półokrąg `r=50` -> `50π` |
| Punkt łuku | `O+R(t*theta)(A-O)` | `t=0.5` obraca o połowę kąta |
| Kąt wektorów | `atan2(det(u,v),dot(u,v))` | Od `(1,-1)` do `(1,0)` jest 45° |
| Baza potęgowa | `B(t)=a*t³+b*t²+c*t+d` | Dla przykładu `x=-6t³+9t²` |
| Pierwsza różnica | `a*h³+b*h²+c*h` | Dla `h=1/4`: `(0.46875,1.6875)` |
| Druga różnica | `6*a*h³+2*b*h²` | `(0.5625,-1.125)` |
| Trzecia różnica | `6*a*h³` | `(-0.5625,0)` |
| Bresenham | integer error + kroki X/Y | `(0,0)->(5,2)` daje 6 pikseli |
| Liang–Barsky | zawężaj przedział `t` | `[-5,15]` przycięte do `[0,10]` |
| Winding | wewnątrz, jeśli suma podpisanych przecięć != 0 | Środek gwiazdy: wartość bezwzględna 2 |

### 8.5. Co mieć otwarte podczas oddawania

Przygotuj uruchomioną aplikację, ten dokument i następujące pliki:

- [Main.kt](/Users/mikolajzawrotniak/Documents/ChatGPT/Projekt1_grafika_komputerowa/src/main/kotlin/pl/edu/pw/polygons/Main.kt)
- [Geometry.kt](/Users/mikolajzawrotniak/Documents/ChatGPT/Projekt1_grafika_komputerowa/src/main/kotlin/pl/edu/pw/polygons/model/Geometry.kt)
- [CircularArc.kt](/Users/mikolajzawrotniak/Documents/ChatGPT/Projekt1_grafika_komputerowa/src/main/kotlin/pl/edu/pw/polygons/model/CircularArc.kt)
- [DrawingAlgorithms.kt](/Users/mikolajzawrotniak/Documents/ChatGPT/Projekt1_grafika_komputerowa/src/main/kotlin/pl/edu/pw/polygons/drawing/DrawingAlgorithms.kt)
- [SceneRenderer.kt](/Users/mikolajzawrotniak/Documents/ChatGPT/Projekt1_grafika_komputerowa/src/main/kotlin/pl/edu/pw/polygons/drawing/SceneRenderer.kt)
- [DemoScene.kt](/Users/mikolajzawrotniak/Documents/ChatGPT/Projekt1_grafika_komputerowa/src/main/kotlin/pl/edu/pw/polygons/model/DemoScene.kt)

Gdy prowadzący wskaże dowolny z głównych algorytmów, spróbuj na małym przykładzie wykonać jego dwa–trzy kroki ręcznie. Umiejętność policzenia `B(0.5)`, jednej iteracji Bresenhama albo promienia z cięciwy i kąta jest lepszym potwierdzeniem zrozumienia niż samo pamiętanie nazwy funkcji.
