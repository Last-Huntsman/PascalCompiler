{ Пример 2: Рекурсивные функции, строки, различные операторы }
program PascalDemo;

var
  n, result: integer;
  ch: char;
  flag: boolean;
  greeting: string;

function isPrime(n: integer): boolean;
var
  i: integer;
  prime: boolean;
begin
  if n < 2 then
  begin
    isPrime := false;
    exit
  end;
  prime := true;
  i := 2;
  while i * i <= n do
  begin
    if n mod i = 0 then
    begin
      prime := false;
      break
    end;
    inc(i)
  end;
  isPrime := prime
end;


begin
  writeln('Простые числа до 20:');
  for n := 2 to 20 do
    if isPrime(n) then
      write(n, ' ');
  writeln
end.