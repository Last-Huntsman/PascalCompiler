{ Пример 3: Работа со строками, ввод/вывод, системные функции }
program StringsAndIO;

var
  s, t, result: string;
  n, i: integer;
  found: integer;

{ Подсчёт количества вхождений символа в строку }
function countChar(s: string; ch: char): integer;
var
  i, cnt: integer;
begin
  cnt := 0;
  for i := 1 to length(s) do
    if s[i] = ch then
      inc(cnt);
  countChar := cnt
end;

{ Переворот строки }
function reverseStr(s: string): string;
var
  i: integer;
  r: string;
begin
  r := '';
  for i := length(s) downto 1 do
    r := r + s[i];
  reverseStr := r
end;

{ Проверка: является ли строка палиндромом }
function isPalindrome(s: string): boolean;
begin
  isPalindrome := (s = reverseStr(s))
end;

begin
  writeln('=== Работа со строками ===');
  writeln;

  s := 'Hello, Pascal!';
  writeln('Строка: ', s);
  writeln('Длина: ', length(s));
  writeln;

  { Поиск подстроки }
  t := 'Pascal';
  found := pos(t, s);
  if found > 0 then
    writeln('Подстрока "', t, '" найдена на позиции: ', found)
  else
    writeln('Подстрока не найдена');

  { Извлечение подстроки }
  result := copy(s, 1, 5);
  writeln('copy(s, 1, 5) = "', result, '"');
  writeln;

  { Подсчёт символов }
  writeln('Кол-во "l" в "', s, '": ', countChar(s, 'l'));
  writeln;

  { Палиндромы }
  writeln('=== Проверка палиндромов ===');
  s := 'racecar';
  writeln('"', s, '" — палиндром: ', isPalindrome(s));
  s := 'hello';
  writeln('"', s, '" — палиндром: ', isPalindrome(s));
  s := 'abcba';
  writeln('"', s, '" — палиндром: ', isPalindrome(s));
  writeln;

  { Конкатенация строк и чисел }
  writeln('=== Конкатенация ===');
  n := 42;
  s := 'Ответ: ';
  writeln(s, n);

  { Цикл с вводом (закомментирован для автозапуска) }
  { writeln('Введите строку:');
    readln(s);
    writeln('Вы ввели: ', s);
    writeln('Перевёрнутая: ', reverseStr(s)); }

  writeln;
  writeln('=== Работа с символами ===');
  s := 'ABCDEF';
  writeln('Строка: ', s);
  write('Символы: ');
  for i := 1 to length(s) do
  begin
    write(s[i]);
    if i < length(s) then write('-')
  end;
  writeln
end.
