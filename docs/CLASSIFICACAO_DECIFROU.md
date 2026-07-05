# Classificação Decifrou

## Objetivo

O resultado público do app é uma letra de `A` a `E`. O valor numérico interno existe
somente para combinar critérios e ordenar produtos; ele não deve ser exibido como uma
nota exata.

## Dados do produto

- Fonte aberta principal: Open Food Facts API v3, com consulta universal por código de barras.
- O Open Products Facts não é usado na nutrição porque não fornece tabela nutricional,
  Nutri-Score ou grupo NOVA.
- FatSecret e Edamam podem ampliar a cobertura de códigos brasileiros, mas exigem
  credenciais privadas. Uma integração futura deve passar por um backend; segredos não
  podem ser incluídos no APK.
- USDA FoodData Central também exige chave e tem cobertura limitada para produtos
  brasileiros. Pode ser um provedor opcional futuro, não uma base para o resultado atual.

## Critérios combinados

1. Nutri-Score oficial, quando a fonte retorna uma letra válida.
2. Composição por 100 g ou 100 ml: energia, açúcares, gordura saturada e sódio são
   obrigatórios para uma estimativa independente. Fibras e proteínas podem melhorar o
   resultado, mas nunca compensam dados críticos ausentes.
3. Limites de rotulagem frontal da Anvisa para açúcares adicionados, gordura saturada e
   sódio. Um, dois ou três alertas limitam progressivamente a melhor letra possível.
4. Perfil nutricional da OPAS para excesso de açúcares adicionados, gordura total,
   gordura saturada, gordura trans e sódio em relação à energia.
5. Grupo NOVA, quando disponível, para representar o grau de processamento segundo o
   Guia Alimentar para a População Brasileira.

Os critérios adicionais podem rebaixar uma letra oficial por alertas ou processamento,
mas nunca melhorar o resultado além do Nutri-Score informado pela fonte.

## Regras de segurança

- Campo ausente nunca vale zero.
- Sem Nutri-Score válido, só há letra quando energia, açúcares, gordura saturada e sódio
  estão presentes para uma classificação completa.
- Quando a tabela está incompleta, o app pode apresentar uma `Estimativa parcial` usando
  pelo menos dois níveis nutricionais estruturados, grupo NOVA, ingredientes industriais
  inequívocos ou categorias reconhecíveis. Categorias com receita muito variável, como
  pastas de grão-de-bico, ficam no nível intermediário até existir uma tabela confiável.
- Estimativas parciais nunca recebem `A`, exceto água mineral identificada com segurança;
  a melhor letra geral nesse modo é `B` e a limitação aparece na tela.
- Todo produto encontrado recebe uma letra. Quando não existe nenhuma evidência além da
  identificação, o app mostra `C provisório` como ponto neutro e avisa que isso não
  representa uma avaliação completa.
- Registros antigos sem a nova letra são invalidados e consultados novamente.
- A classificação é uma orientação geral e não substitui avaliação médica ou nutricional.

## Faixas internas

- `A`: qualidade interna de 80 a 100.
- `B`: 65 a 79.
- `C`: 45 a 64.
- `D`: 25 a 44.
- `E`: 0 a 24.

O usuário vê apenas a letra e a explicação dos critérios utilizados.

## Referências

- Open Food Facts API: https://openfoodfacts.github.io/documentation/
- Anvisa, rotulagem nutricional: https://www.gov.br/anvisa/pt-br/assuntos/alimentos/rotulagem/rotulagem-nutricional/
- OPAS, modelo de perfil nutricional: https://www.paho.org/pt/nutrient-profile-model
- Guia Alimentar para a População Brasileira: https://www.gov.br/saude/pt-br/assuntos/saude-brasil/publicacoes-para-promocao-a-saude/guia_alimentar_populacao_brasileira_2ed.pdf/view/
