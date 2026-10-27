# Mysz i klawiatura
Lewy przycisk myszy wybiera i przeciąga element. Wierzchołek zmienia kształt, pomarańczowy uchwyt zmienia Béziera, a krawędź lub wnętrze przesuwają cały wielokąt. Pozostałe przyciski nie rozpoczynają edycji. Kliknięcie obok środka uchwytu zachowuje początkowy odstęp od kursora.

Kliknij jedną krawędź, aby ustawić długość. Kliknij dwie krawędzie kolejno, aby ustawić prostopadłość; mogą należeć do różnych wielokątów. Ponowne kliknięcie krawędzi usuwa ją z wyboru. Relacje usuwa się przyciskiem „Usuń” na ich liście.

F1 otwiera pomoc. Escape zamyka pomoc albo kończy przeciąganie i czyści wybór. Po wybraniu wierzchołka Delete usuwa go (pozostają co najmniej trzy). Skróty działają w obszarze edytora; podczas wpisywania długości działa zwykła edycja tekstu.

# Edycja wielokątów
Wielokąty są przygotowane w scenie początkowej; ich liczbę można zmienić w kodzie w DemoScene.kt. Samoprzecięcia są dozwolone.

Po wybraniu krawędzi można dodać wierzchołek, zmienić ją w Béziera lub łuk, a następnie przywrócić odcinek. Podział odbywa się w t=0.5; dla krzywej nie oznacza to środka cięciwy. Usunięcie wierzchołka i podział usuwają relacje zmienionych krawędzi. Zmiany topologii są ponownie sprawdzane.

# Krzywe i ciągłość
Bézier trzeciego stopnia ma dwa końce i dwa uchwyty. Przerywana łamana łączy początek, oba uchwyty i koniec. G0 oznacza wspólny punkt, G1 wspólny kierunek stycznych o zgodnym zwrocie, a C1 również równe wektory stycznych. Domyślne C1 jest ustawiane przy konwersji, jeśli nie wybrano wcześniej klasy ręcznie.

Przeciągany uchwyt steruje ciągłością. Przy samym G1/C1 końce tego segmentu pozostają na miejscu, a sąsiedni uchwyt lub dalszy wierzchołek odcinka może się przesunąć. Zadana długość krzywej jest przykładem relacji, która może wymagać ruchu jej końców. Przyjęta edycja zachowuje pozycję przeciąganego punktu; nierozwiązany układ jest odrzucany z komunikatem.

Łuk dopuszcza G0 i G1, przy czym G1 tylko na jednym końcu. G0 na obu końcach daje półokrąg: promień to połowa długości cięciwy. G1 wyznacza okrąg z końców i kierunku sąsiedniej stycznej. Zerowa cięciwa, zerowa styczna i styczna równoległa do cięciwy nie wyznaczają wymaganego skończonego łuku. Przełączenie z C1 w łuk ustawia G0. Podział łuku może wymagać zmiany kształtu części, jeśli zewnętrzny koniec miał już G1.

# Relacje i ich rozwiązywanie
Długość odcinka to norma wektora różnicy końców; długość Béziera jest sumą 40 krótkich odcinków; długość łuku okręgu wynosi r razy bezwzględny kąt w radianach. Prostopadłość krzywych porównuje ich CIĘCIWY. Jedna krawędź może uczestniczyć w kilku relacjach.

Solver pracuje na roboczej kopii sceny. Wykonuje 16 przebiegów: długości i prostopadłości, przeliczenie łuków, ciągłość. Przy edycji graf prostopadłości jest przeszukiwany wszerz (BFS): jedna aktywna krawędź steruje każdą spójną grupą. Odległość od niej ustala kierunek propagacji. Przy remisie decyduje stabilne ID. Korekty chronią przeciągany punkt i wspólne kotwice. Dla długości błąd musi być najwyżej 0.00001; dla prostopadłości sprawdzamy iloczyn skalarny jednostkowych kierunków bliski zeru.

Końcowa walidacja sprawdza wszystkie relacje, ciągłości, łuki i pozycję przeciąganego punktu. Wynik jest zatwierdzany w całości albo zostaje poprzednia scena. Odmowa po 16 przebiegach nie jest dowodem matematycznej sprzeczności: iteracyjny solver może nie znaleźć rozwiązania. Nie jest to pełny solver CAD ani minimalizacja wszystkich przesunięć.

# Model danych i architektura
Point opisuje położenie, Vector przesunięcie lub kierunek. Vertex i Edge mają stałe identyfikatory. Polygon przechowuje uporządkowane wierzchołki oraz tyle samo krawędzi; krawędź i łączy wierzchołki i oraz (i+1) modulo liczba wierzchołków. EdgeShape to odcinek, Bézier z dwoma uchwytami albo łuk z podpisanym kątem. Relacje wskazują ID krawędzi; ciągłość jest przypisana do ID wierzchołka. Scene przechowuje wielokąty, relacje, ciągłości i licznik ID.

model/ zawiera geometrię i solver bez Compose. Geometry.kt opisuje punkty i wektory, Edge.kt i EdgeGeometry.kt krawędzie, Scene.kt edycję sceny, Constraints.kt relacje, ConstraintSolver.kt ich rozwiązywanie, BezierEditing.kt podział krzywej, a HitTesting.kt wykrywanie trafień. drawing/DrawingAlgorithms.kt zawiera czyste algorytmy; drawing/SceneRenderer.kt rysowanie; interaction/DragGesture.kt przelicza położenie kursora na ruch obiektu; Main.kt obsługuje interfejs, wybór i zdarzenia. ui/HelpDialog.kt wyświetla tę dokumentację. Kopiowanie modelu zapewnia transakcyjne odrzucanie operacji.

# Algorytmy rysowania
Radio button wybiera biblioteczne rysowanie odcinków albo własnego Bresenhama. Bresenham używa całkowitych współrzędnych i akumulatora błędu, działa we wszystkich oktantach; Compose nanosi wybrane piksele. Przed rasteryzacją Liang–Barsky przycina niewidoczne części odcinków.

Bézier jest zawsze liczony własnym algorytmem przyrostowym: przejście z bazy Bernsteina do B(t)=a*t³+b*t²+c*t+d, a następnie aktualizacja punktu i trzech różnic skończonych przez dodawanie. Liczba próbek zależy od rozmiaru wieloboku kontrolnego na ekranie, z limitem 100000. Próbki łączy wybrany algorytm odcinków. Łuki rysuje biblioteczne arcTo. Wybór wnętrza używa reguły non-zero winding, więc można wybrać również środek samoprzecinającej się gwiazdy.

# Scena początkowa i założenia
Po starcie są trzy wielokąty: samoprzecinająca się gwiazda, pięciokąt z Bézierem oraz czworokąt z półokręgiem. Górna krawędź czworokąta ma długość 160 i jest prostopadła do prawej. Scenę odtwarza „Przywróć scenę”. W DemoScene.kt wystarczy dopisać kolejne wywołanie pomocniczej funkcji polygon; identyfikatory przydzielają się automatycznie.

Geometria jest liczona w logicznym układzie 900×470. Canvas dopasowuje go proporcjonalnie i centruje; punkty mogą wyjść poza jego widoczny obszar. Model dopuszcza samoprzecięcia. Nie ma zapisu, wczytywania ani undo/redo, a przywrócenie sceny usuwa bieżące zmiany. Pakiet instalacyjny należy budować na docelowym systemie; działanie na Windows wymaga osobnego sprawdzenia na Windows.
