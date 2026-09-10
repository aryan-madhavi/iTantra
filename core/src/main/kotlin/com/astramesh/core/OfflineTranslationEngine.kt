package com.astramesh.core

/**
 * Offline Rule, Phrasebook & Vocabulary Translation Engine.
 * Supports on-demand receiver-side translation between 10 languages without internet or cloud APIs.
 * Preserves numbers, coordinates, and proper nouns.
 */
object OfflineTranslationEngine {

    // Common Emergency, Rescue, Health, Directions, and Greeting phrases mapped across languages
    private val PHRASE_INDEX: Map<String, Map<Language, String>> = mapOf(
        "help_me" to mapOf(
            Language.ENGLISH to "Please help me.",
            Language.HINDI to "कृपया मेरी मदद कीजिए।",
            Language.GUJARATI to "મને મદદ કરો.",
            Language.MARATHI to "मला मदत करा.",
            Language.TAMIL to "எனக்கு உதவுங்கள்.",
            Language.TELUGU to "నాకు సహాయం చేయండి.",
            Language.KANNADA to "ನನಗೆ ಸಹಾಯ ಮಾಡಿ.",
            Language.MALAYALAM to "എന്നെ സഹായിക്കൂ.",
            Language.ODIA to "ମୋତେ ସାହାଯ୍ୟ କରନ୍ତୁ।",
            Language.BENGALI to "আমাকে সাহায্য করুন।"
        ),
        "fire_here" to mapOf(
            Language.ENGLISH to "There is a fire here.",
            Language.HINDI to "यहाँ आग लगी है।",
            Language.GUJARATI to "અહીં આગ લાગી છે.",
            Language.MARATHI to "येथे आग लागली आहे.",
            Language.TAMIL to "இங்கே தீ விபத்து ஏற்பட்டுள்ளது.",
            Language.TELUGU to "ఇక్కడ అగ్ని ప్రమాదం జరిగింది.",
            Language.KANNADA to "ಇಲ್ಲಿ ಬೆಂಕಿ ಬಿದ್ದಿದೆ.",
            Language.MALAYALAM to "ഇവിടെ തീപിടിത്തം ഉണ്ടായിട്ടുണ്ട്.",
            Language.ODIA to "ଏଠାରେ ନିଆଁ ଲାଗିଛି।",
            Language.BENGALI to "এখানে আগুন লেগেছে।"
        ),
        "medical_emergency" to mapOf(
            Language.ENGLISH to "Send medical help immediately.",
            Language.HINDI to "तुरंत चिकित्सा सहायता भेजें।",
            Language.GUJARATI to "તરત જ તબીબી સહાય મોકલો.",
            Language.MARATHI to "त्वरित वैद्यकीय मदत पाठवा.",
            Language.TAMIL to "உடனடியாக மருத்துவ உதவி அனுப்பவும்.",
            Language.TELUGU to "వెంటనే వైద్య సహాయం పంపండి.",
            Language.KANNADA to "ತಕ್ಷಣ ವೈದ್ಯಕೀಯ ಸಹಾಯ ಕಳುಹಿಸಿ.",
            Language.MALAYALAM to "ഉടൻ തന്നെ വൈദ്യസഹായം അയക്കൂ.",
            Language.ODIA to "ତୁରନ୍ତ ଡାକ୍ତରୀ ସହାୟତା ପଠାନ୍ତୁ।",
            Language.BENGALI to "অবিলম্বে চিকিৎসা সহায়তা পাঠান।"
        ),
        "trapped" to mapOf(
            Language.ENGLISH to "We are trapped and need rescue.",
            Language.HINDI to "हम फंसे हुए हैं और बचाव की जरूरत है।",
            Language.GUJARATI to "અમે ફસાયેલા છીએ અને બચાવની જરૂર છે.",
            Language.MARATHI to "आम्ही अडकलो आहोत आणि बचावाची गरज आहे.",
            Language.TAMIL to "நாங்கள் மாட்டிக்கொண்டுள்ளோம், மீட்பு தேவை.",
            Language.TELUGU to "మేము చిక్కుకున్నాము మరియు రక్షణ అవసరం.",
            Language.KANNADA to "ನಾವು ಸಿಲುಕಿಕೊಂಡಿದ್ದೇವೆ ಮತ್ತು ರಕ್ಷಣೆ ಅಗತ್ಯವಿದೆ.",
            Language.MALAYALAM to "ഞങ്ങൾ കുടുങ്ങിക്കിടക്കുകയാണ്, രക്ഷിക്കണം.",
            Language.ODIA to "ଆମେ ଫସି ରହିଛୁ ଏବଂ ଉଦ୍ଧାର ଆବଶ୍ୟକ।",
            Language.BENGALI to "আমরা আটকে পড়েছি এবং উদ্ধার দরকার।"
        ),
        "safe_location" to mapOf(
            Language.ENGLISH to "We are safe here.",
            Language.HINDI to "हम यहाँ सुरक्षित हैं।",
            Language.GUJARATI to "અમે અહીં સુરક્ષિત છીએ.",
            Language.MARATHI to "आम्ही येथे सुरक्षित आहोत.",
            Language.TAMIL to "நாங்கள் இங்கே பாதுகாப்பாக இருக்கிறோம்.",
            Language.TELUGU to "మేము ఇక్కడ సురక్షితంగా ఉన్నాము.",
            Language.KANNADA to "ನಾವು ಇಲ್ಲಿ ಸುರಕ್ಷಿತವಾಗಿದ್ದೇವೆ.",
            Language.MALAYALAM to "ഞങ്ങൾ ഇവിടെ സുരക്ഷിതരാണ്.",
            Language.ODIA to "ଆମେ ଏଠାରେ ସୁରକ୍ଷିତ ଅଛୁ।",
            Language.BENGALI to "আমরা এখানে নিরাপদ আছি।"
        ),
        "need_water_food" to mapOf(
            Language.ENGLISH to "We need clean water and food.",
            Language.HINDI to "हमें पीने का पानी और भोजन चाहिए।",
            Language.GUJARATI to "અમને પીવાનું પાણી અને ખોરાક જોઈએ છે.",
            Language.MARATHI to "आम्हाला पिण्याचे पाणी आणि अन्न हवे आहे.",
            Language.TAMIL to "எங்களுக்கு குடிநீரும் உணவும் தேவை.",
            Language.TELUGU to "మాకు తాగునీరు మరియు ఆహారం అవసరం.",
            Language.KANNADA to "ನಮಗೆ ಕುಡಿಯುವ ನೀರು ಮತ್ತು ಆಹಾರ ಬೇಕು.",
            Language.MALAYALAM to "ഞങ്ങൾക്ക് കുടിവെള്ളവും ഭക്ഷണവും വേണം.",
            Language.ODIA to "ଆମକୁ ପିଇବା ପାଣି ଏବଂ ଖାଦ୍ୟ ଦରକାର।",
            Language.BENGALI to "আমাদের পানীয় জল এবং খাবার প্রয়োজন।"
        ),
        "road_blocked" to mapOf(
            Language.ENGLISH to "The road is blocked due to debris.",
            Language.HINDI to "मलबे के कारण रास्ता बंद है।",
            Language.GUJARATI to "કાટમાળને કારણે રસ્તો બંધ છે.",
            Language.MARATHI to "ढिगाऱ्यामुळे रस्ता बंद आहे.",
            Language.TAMIL to "இடிபாடுகளால் சாலை அடைக்கப்பட்டுள்ளது.",
            Language.TELUGU to "శిథిలాల వల్ల రహదారి మూసివేయబడింది.",
            Language.KANNADA to "ಅವಶೇಷಗಳಿಂದ ರಸ್ತೆ ಮುಚ್ಚಲ್ಪಟ್ಟಿದೆ.",
            Language.MALAYALAM to "അവശിഷ്ടങ്ങൾ കാരണം റോഡ് തടസ്സപ്പെട്ടു.",
            Language.ODIA to "ଭଙ୍ଗା ଅବଶେଷ ଯୋଗୁଁ ରାସ୍ତା ବନ୍ଦ ଅଛି।",
            Language.BENGALI to "ধ্বংসাবশেষের কারণে রাস্তা বন্ধ।"
        ),
        "hello" to mapOf(
            Language.ENGLISH to "Hello.",
            Language.HINDI to "नमस्ते।",
            Language.GUJARATI to "નમસ્તે.",
            Language.MARATHI to "नमस्कार.",
            Language.TAMIL to "வணக்கம்.",
            Language.TELUGU to "నమస్కారం.",
            Language.KANNADA to "ನಮಸ್ಕಾರ.",
            Language.MALAYALAM to "നമസ്കാരം.",
            Language.ODIA to "ନମସ୍କାର।",
            Language.BENGALI to "নমস্কার।"
        ),
        "how_are_you" to mapOf(
            Language.ENGLISH to "How are you?",
            Language.HINDI to "आप कैसे हैं?",
            Language.GUJARATI to "તમે કેમ છો?",
            Language.MARATHI to "तुम्ही कसे आहात?",
            Language.TAMIL to "நீங்கள் எப்படி இருக்கிறீர்கள்?",
            Language.TELUGU to "మీరు ఎలా ఉన్నారు?",
            Language.KANNADA to "ನೀವು ಹೇಗಿದ್ದೀರಿ?",
            Language.MALAYALAM to "സുഖമാണോ?",
            Language.ODIA to "ଆପଣ କେମିତି ଅଛନ୍ତି?",
            Language.BENGALI to "আপনি কেমন আছেন?"
        ),
        "thank_you" to mapOf(
            Language.ENGLISH to "Thank you.",
            Language.HINDI to "धन्यवाद।",
            Language.GUJARATI to "આભાર.",
            Language.MARATHI to "धन्यवाद.",
            Language.TAMIL to "நன்றி.",
            Language.TELUGU to "ధన్యవాదాలు.",
            Language.KANNADA to "ಧನ್ಯವಾದಗಳು.",
            Language.MALAYALAM to "നന്ദി.",
            Language.ODIA to "ଧନ୍ୟବାଦ।",
            Language.BENGALI to "ধন্যবাদ।"
        ),
        "voice_message_received" to mapOf(
            Language.ENGLISH to "Voice message received.",
            Language.HINDI to "वॉयस संदेश प्राप्त हुआ।",
            Language.GUJARATI to "વૉઇસ સંદેશ મળ્યો.",
            Language.MARATHI to "व्हॉइस संदेश प्राप्त झाला.",
            Language.TAMIL to "குரல் செய்தி பெறப்பட்டது.",
            Language.TELUGU to "వాయిస్ సందేశం అందింది.",
            Language.KANNADA to "ಧ್ವನಿ ಸಂದೇಶ ಸ್ವೀಕರಿಸಲಾಗಿದೆ.",
            Language.MALAYALAM to "വോയ്‌സ് സന്ദേശം ലഭിച്ചു.",
            Language.ODIA to "ଭଏସ୍ ବାର୍ତ୍ତା ମିଳିଲା।",
            Language.BENGALI to "ভয়েস বার্তা প্রাপ্ত হয়েছে।"
        ),
        "emergency_sos" to mapOf(
            Language.ENGLISH to "Emergency Alert! Immediate assistance required!",
            Language.HINDI to "आपातकालीन चेतावनी! तत्काल सहायता चाहिए!",
            Language.GUJARATI to "ઇમરજન્સી એલર્ટ! તાત્કાલિક સહાયની જરૂર છે!",
            Language.MARATHI to "आणीबाणी इशारा! त्वरित मदतीची गरज आहे!",
            Language.TAMIL to "அவசர எச்சரிக்கை! உடனடி உதவி தேவை!",
            Language.TELUGU to "అత్యవసర హెచ్చరిక! వెంటనే సహాయం కావాలి!",
            Language.KANNADA to "ತುರ್ತು ಎಚ್ಚರಿಕೆ! ತಕ್ಷಣದ ನೆರವು ಬೇಕು!",
            Language.MALAYALAM to "അടിയന്തര മുന്നറിയിപ്പ്! ഉടൻ സഹായം ആവശ്യമാണ്!",
            Language.ODIA to "ଜରୁରୀକାଳୀନ ସତର୍କତା! ତୁରନ୍ତ ସହାୟତା ଆବଶ୍ୟକ!",
            Language.BENGALI to "জরুরী সতর্কতা! অবিলম্বে সহায়তা প্রয়োজন!"
        ),
        "emergency_distress" to mapOf(
            Language.ENGLISH to "Emergency assistance is required.",
            Language.HINDI to "आपातकालीन सहायता की आवश्यकता है।",
            Language.GUJARATI to "કટોકટી સહાયની જરૂર છે.",
            Language.MARATHI to "तातडीच्या मदतीची आवश्यकता आहे.",
            Language.TAMIL to "அவசர உதவி தேவைப்படுகிறது.",
            Language.TELUGU to "అత్యవసర సహాయం అవసరం.",
            Language.KANNADA to "ತುರ್ತು ನೆರವಿನ ಅಗತ್ಯವಿದೆ.",
            Language.MALAYALAM to "അടിയന്തര സഹായം ആവശ്യമാണ്.",
            Language.ODIA to "ଜରୁରୀକାଳୀନ ସହାୟତା ଆବଶ୍ୟକ ଅଟେ।",
            Language.BENGALI to "জরুরী সহায়তার প্রয়োজন।"
        ),
        "can_you_hear_me" to mapOf(
            Language.ENGLISH to "Can you hear me?",
            Language.HINDI to "क्या आप मुझे सुन सकते हैं?",
            Language.GUJARATI to "શું તમે મને સાંભળી શકો છો?",
            Language.MARATHI to "तुम्ही मला ऐकू शकता का?",
            Language.TAMIL to "நீங்கள் என்னை கேட்க முடிகிறதா?",
            Language.TELUGU to "మీరు నన్ను వినగలరా?",
            Language.KANNADA to "ನೀವು ನನ್ನನ್ನು ಕೇಳಬಹುದೇ?",
            Language.MALAYALAM to "നിങ്ങൾക്ക് എന്നെ കേൾക്കാമോ?",
            Language.ODIA to "ଆପଣ ମୋତେ ଶୁଣିପାରୁଛନ୍ତି କି?",
            Language.BENGALI to "আপনি কি আমাকে শুনতে পাচ্ছেন?"
        ),
        "where_are_you" to mapOf(
            Language.ENGLISH to "Where are you?",
            Language.HINDI to "आप कहाँ हैं?",
            Language.GUJARATI to "તમે ક્યાં છો?",
            Language.MARATHI to "तुम्ही कुठे आहात?",
            Language.TAMIL to "நீங்கள் எங்கே இருக்கிறீர்கள்?",
            Language.TELUGU to "మీరు ఎక్కడ ఉన్నారు?",
            Language.KANNADA to "ನೀವು ಎಲ್ಲಿದ್ದೀರಿ?",
            Language.MALAYALAM to "നിങ്ങൾ എവിടെയാണ്?",
            Language.ODIA to "ଆପଣ କେଉଁଠାରେ ଅଛନ୍ତି?",
            Language.BENGALI to "আপনি কোথায় আছেন?"
        ),
        "i_am_here" to mapOf(
            Language.ENGLISH to "I am here.",
            Language.HINDI to "मैं यहाँ हूँ।",
            Language.GUJARATI to "હું અહીં છું.",
            Language.MARATHI to "मी येथे आहे.",
            Language.TAMIL to "நான் இங்கே இருக்கிறேன்.",
            Language.TELUGU to "నేను ఇక్కడ ఉన్నాను.",
            Language.KANNADA to "ನಾನು ಇಲ್ಲಿದ್ದೇನೆ.",
            Language.MALAYALAM to "ഞാൻ ഇവിടെയുണ്ട്.",
            Language.ODIA to "ମୁଁ ଏଠାରେ ଅଛି।",
            Language.BENGALI to "আমি এখানে আছি।"
        ),
        "come_quickly" to mapOf(
            Language.ENGLISH to "Please come quickly.",
            Language.HINDI to "कृपया जल्दी आइए।",
            Language.GUJARATI to "કૃપા કરીને ઝડપથી આવો.",
            Language.MARATHI to "कृपया लवकर या.",
            Language.TAMIL to "தயவுசெய்து சீக்கிரம் வாருங்கள்.",
            Language.TELUGU to "దయచేసి త్వరగా రండి.",
            Language.KANNADA to "ದಯವಿಟ್ಟು ಬೇಗ ಬನ್ನಿ.",
            Language.MALAYALAM to "ദയവായി വേഗം വരൂ.",
            Language.ODIA to "ଦୟାକରି ଶୀଘ୍ର ଆସନ୍ତୁ।",
            Language.BENGALI to "দয়া করে তাড়াতাড়ি আসুন।"
        ),
        "all_ok" to mapOf(
            Language.ENGLISH to "Everything is all right.",
            Language.HINDI to "सब कुछ ठीक है।",
            Language.GUJARATI to "બધું બરાબર છે.",
            Language.MARATHI to "सर्व काही ठीक आहे.",
            Language.TAMIL to "எல்லாம் சரியாக உள்ளது.",
            Language.TELUGU to "అంతా బాగానే ఉంది.",
            Language.KANNADA to "ಎಲ್ಲವೂ ಸರಿಯಾಗಿದೆ.",
            Language.MALAYALAM to "എല്ലാം ശരിയാണ്.",
            Language.ODIA to "ସବୁକିଛି ଠିକ୍ ଅଛି।",
            Language.BENGALI to "সবকিছু ঠিক আছে।"
        ),
        "alpha_perimeter" to mapOf(
            Language.ENGLISH to "Alpha-7 reporting in, Sector B perimeter clear.",
            Language.HINDI to "अल्फा-7 की तरफ से सन्देश: सेक्टर बी सुरक्षित है।",
            Language.GUJARATI to "આલ્ફા-7 તરફથી સંદેશ: સેક્ટર બી સુરક્ષિત છે.",
            Language.MARATHI to "अल्फा-७ कडून संदेश: क्षेत्र बी सुरक्षित आहे.",
            Language.TAMIL to "ஆல்ஃபா-7 செய்தி: செக்டார் பி பாதுகாப்பாக உள்ளது.",
            Language.TELUGU to "ఆల్ఫా-7 నుండి సందేశం: సెక్టార్ బి సురక్షితంగా ఉంది.",
            Language.KANNADA to "ಆಲ್ಫಾ-7 ನಿಂದ ಸಂದೇಶ: ಸೆಕ್ಟರ್ ಬಿ ಸುರಕ್ಷಿತವಾಗಿದೆ.",
            Language.MALAYALAM to "ആൽഫ-7 സന്ദേശം: സെക്ടർ ബി സുരക്ഷിതമാണ്.",
            Language.ODIA to "ଆଲଫା-୭ ରୁ ବାର୍ତ୍ତା: ସେକ୍ଟର ବି ସୁରକ୍ଷିତ ଅଛି।",
            Language.BENGALI to "আলফা-৭ থেকে বার্তা: সেক্টর বি নিরাপদ রয়েছে।"
        ),
        "mesh_relay_ready" to mapOf(
            Language.ENGLISH to "Acknowledged. Standing by for mesh relay packet.",
            Language.HINDI to "स्वीकृत। मेश रिले प्रसारण के लिए तैयार हैं।",
            Language.GUJARATI to "સ્વીકાર્યું. મેશ રિલે પ્રસારણ માટે તૈયાર છે.",
            Language.MARATHI to "स्वीकृत. मेश रिले प्रक्षेपणासाठी तयार आहोत.",
            Language.TAMIL to "ஒப்புக்கொள்ளப்பட்டது. மெஷ் ரிலே தயார் நிலையில் உள்ளது.",
            Language.TELUGU to "అంగీకరించబడింది. మెష్ రిలే ప్రసారానికి సిద్ధంగా ఉంది.",
            Language.KANNADA to "ಸ್ವೀಕರಿಸಲಾಗಿದೆ. ಮೆಶ್ ರಿಲೇ ಪ್ರಸಾರಕ್ಕೆ ಸಿದ್ಧವಾಗಿದೆ.",
            Language.MALAYALAM to "അംഗീകരിച്ചു. മെഷ് റിലേ പ്രക്ഷേപണത്തിന് തയ്യാറാണ്.",
            Language.ODIA to "ସ୍ୱୀକୃତ। ମେଶ୍ ରିଲେ ପ୍ରସାରଣ ପାଇଁ ପ୍ରସ୍ତୁତ।",
            Language.BENGALI to "স্বীকৃত। মেশ রিলে সম্প্রচারের জন্য প্রস্তুত।"
        ),
        "p2p_connection_stable" to mapOf(
            Language.ENGLISH to "Loud and clear. Off-grid P2P connection stable.",
            Language.HINDI to "आवाज़ बिल्कुल साफ है। ऑफ-ग्रिड P2P संपर्क स्थापित है।",
            Language.GUJARATI to "અવાજ એકદમ સ્પષ્ટ છે. ઓફલાઇન જોડાણ ચાલુ છે.",
            Language.MARATHI to "आवाज स्पष्ट येत आहे. पी२पी नेटवर्क सुरू आहे.",
            Language.TAMIL to "சிக்னல் தெளிவாக உள்ளது. ஆஃப்லைன் இணைப்பு தயார்.",
            Language.TELUGU to "సిగ్నల్ స్పష్టంగా ఉంది. ఆఫ్లైన్ కనెక్షన్ సిద్ధంగా ఉంది.",
            Language.KANNADA to "ಸಿಗ್ನಲ್ ಸ್ಪಷ್ಟವಾಗಿದೆ. ಆಫ್ಲೈನ್ ಸಂಪರ್ಕ ಸಿದ್ಧವಾಗಿದೆ.",
            Language.MALAYALAM to "ശബ്ദം വ്യക്തമാണ്. ഓഫ്‌ലൈൻ കണക്ഷൻ സുരക്ഷിതമാണ്.",
            Language.ODIA to "ସ୍ପଷ୍ଟ ଶୁଣାଯାଉଛି। ଅଫଲାଇନ୍ ସଂଯୋଗ ପ୍ରସ୍ତୁତ।",
            Language.BENGALI to "স্পষ্ট শোনা যাচ্ছে। অফলাইন সংযোগ প্রস্তুত।"
        ),
        "emergency_team_dispatched" to mapOf(
            Language.ENGLISH to "Emergency assistance team dispatched to Grid 4.",
            Language.HINDI to "आपातकालीन सहायता टीम ग्रिड 4 के लिए रवाना हो गई है।",
            Language.GUJARATI to "કટોકટી સહાય ટીમ ગ્રીડ 4 માટે રવાના થઈ ગઈ છે.",
            Language.MARATHI to "तातडीची मदत टीम ग्रिड ४ कडे रवाना झाली आहे.",
            Language.TAMIL to "அவசர உதவி குழு கிரிட் 4-க்கு அனுப்பப்பட்டுள்ளது.",
            Language.TELUGU to "అత్యవసర సహాయ బృందం గ్రిడ్ 4కు బయలుదేరింది.",
            Language.KANNADA to "ತುರ್ತು ಸಹಾಯ ತಂಡವು ಗ್ರಿಡ್ 4 ಕ್ಕೆ ರವಾನೆಯಾಗಿದೆ.",
            Language.MALAYALAM to "അടിയന്തര സഹായ സംഘം ഗ്രിഡ് 4 ലേക്ക് തിരിച്ചു.",
            Language.ODIA to "ଜରୁରୀକାଳୀନ ଦଳ ଗ୍ରିଡ୍ ୪ କୁ ପଠାଯାଇଛି।",
            Language.BENGALI to "জরুরী সহায়তার দল গ্রিড ৪-এর দিকে রওনা হয়েছে।"
        )
    )

    // High-frequency emergency, tactical, and medical vocabulary across all 10 languages
    private val VOCAB_INDEX: Map<String, Map<Language, String>> = mapOf(
        "help" to mapOf(
            Language.ENGLISH to "help", Language.HINDI to "मदद", Language.GUJARATI to "મદદ",
            Language.MARATHI to "मदत", Language.TAMIL to "உதவி", Language.TELUGU to "సహాయం",
            Language.KANNADA to "ಸಹಾಯ", Language.MALAYALAM to "സഹായം", Language.ODIA to "ସହାୟତା", Language.BENGALI to "সাহায্য"
        ),
        "emergency" to mapOf(
            Language.ENGLISH to "emergency", Language.HINDI to "आपातकाल", Language.GUJARATI to "કટોકટી",
            Language.MARATHI to "आणीबाणी", Language.TAMIL to "அவசரம்", Language.TELUGU to "అత్యవసరం",
            Language.KANNADA to "ತುರ್ತು", Language.MALAYALAM to "അടിയന്തിരം", Language.ODIA to "ଜରୁରୀକାଳୀନ", Language.BENGALI to "জরুরী"
        ),
        "danger" to mapOf(
            Language.ENGLISH to "danger", Language.HINDI to "खतरा", Language.GUJARATI to "જોખમ",
            Language.MARATHI to "धोका", Language.TAMIL to "ஆபத்து", Language.TELUGU to "ప్రమాదం",
            Language.KANNADA to "ಅಪಾಯ", Language.MALAYALAM to "അപകടം", Language.ODIA to "ବିପଦ", Language.BENGALI to "বিপদ"
        ),
        "safe" to mapOf(
            Language.ENGLISH to "safe", Language.HINDI to "सुरक्षित", Language.GUJARATI to "સુરક્ષિત",
            Language.MARATHI to "सुरक्षित", Language.TAMIL to "பாதுகாப்பானது", Language.TELUGU to "సురక్షితం",
            Language.KANNADA to "ಸುರಕ್ಷಿತ", Language.MALAYALAM to "സുരക്ഷിതം", Language.ODIA to "ସୁରକ୍ଷିତ", Language.BENGALI to "নিরাপদ"
        ),
        "water" to mapOf(
            Language.ENGLISH to "water", Language.HINDI to "पानी", Language.GUJARATI to "પાણી",
            Language.MARATHI to "पाणी", Language.TAMIL to "தண்ணீர்", Language.TELUGU to "నీరు",
            Language.KANNADA to "ನೀರು", Language.MALAYALAM to "വെള്ളം", Language.ODIA to "ପାଣି", Language.BENGALI to "জল"
        ),
        "food" to mapOf(
            Language.ENGLISH to "food", Language.HINDI to "भोजन", Language.GUJARATI to "ખોરાક",
            Language.MARATHI to "अन्न", Language.TAMIL to "உணவு", Language.TELUGU to "ఆహారం",
            Language.KANNADA to "ಆಹಾರ", Language.MALAYALAM to "ഭക്ഷണം", Language.ODIA to "ଖାଦ୍ୟ", Language.BENGALI to "খাবার"
        ),
        "fire" to mapOf(
            Language.ENGLISH to "fire", Language.HINDI to "आग", Language.GUJARATI to "આગ",
            Language.MARATHI to "आग", Language.TAMIL to "தீ", Language.TELUGU to "అగ్ని",
            Language.KANNADA to "ಬೆಂಕಿ", Language.MALAYALAM to "തീ", Language.ODIA to "ନିଆଁ", Language.BENGALI to "আগুন"
        ),
        "doctor" to mapOf(
            Language.ENGLISH to "doctor", Language.HINDI to "डॉक्टर", Language.GUJARATI to "ડૉક્ટર",
            Language.MARATHI to "डॉक्टर", Language.TAMIL to "மருத்துவர்", Language.TELUGU to "వైద్యుడు",
            Language.KANNADA to "ವೈದ್ಯರು", Language.MALAYALAM to "ഡോക്ടർ", Language.ODIA to "ଡାକ୍ତର", Language.BENGALI to "ডাক্তার"
        ),
        "hospital" to mapOf(
            Language.ENGLISH to "hospital", Language.HINDI to "अस्पताल", Language.GUJARATI to "હોસ્પિટલ",
            Language.MARATHI to "रुग्णालय", Language.TAMIL to "மருத்துவமனை", Language.TELUGU to "ఆసుపత్రి",
            Language.KANNADA to "ಆಸ್ಪತ್ರೆ", Language.MALAYALAM to "ആശുപത്രി", Language.ODIA to "ଡାକ୍ତରଖାନା", Language.BENGALI to "হাসপাতাল"
        ),
        "ambulance" to mapOf(
            Language.ENGLISH to "ambulance", Language.HINDI to "एम्बुलेंस", Language.GUJARATI to "એમ્બ્યુલન્સ",
            Language.MARATHI to "रुग्णवाहिका", Language.TAMIL to "ஆம்புலன்ஸ்", Language.TELUGU to "అంబులెన్స్",
            Language.KANNADA to "ಆಂಬ್ಯುಲೆನ್ಸ್", Language.MALAYALAM to "ആംബുലൻസ്", Language.ODIA to "ଆମ୍ବୁଲାନ୍ସ", Language.BENGALI to "অ্যাম্বুলেন্স"
        ),
        "police" to mapOf(
            Language.ENGLISH to "police", Language.HINDI to "पुलिस", Language.GUJARATI to "પોલીસ",
            Language.MARATHI to "पोलीस", Language.TAMIL to "காவல்", Language.TELUGU to "పోలీసులు",
            Language.KANNADA to "ಪೊಲೀಸ್", Language.MALAYALAM to "പോലീസ്", Language.ODIA to "ପୋଲିସ", Language.BENGALI to "পুলিশ"
        ),
        "team" to mapOf(
            Language.ENGLISH to "team", Language.HINDI to "टीम", Language.GUJARATI to "ટીમ",
            Language.MARATHI to "संघ", Language.TAMIL to "குழு", Language.TELUGU to "బృందం",
            Language.KANNADA to "ತಂಡ", Language.MALAYALAM to "സംഘം", Language.ODIA to "ଦଳ", Language.BENGALI to "দল"
        ),
        "rescue" to mapOf(
            Language.ENGLISH to "rescue", Language.HINDI to "बचाव", Language.GUJARATI to "બચાવ",
            Language.MARATHI to "बचाव", Language.TAMIL to "மீட்பு", Language.TELUGU to "రక్షణ",
            Language.KANNADA to "ರಕ್ಷಣೆ", Language.MALAYALAM to "രക്ഷാപ്രവർത്തനം", Language.ODIA to "ଉଦ୍ଧାର", Language.BENGALI to "উদ্ধার"
        ),
        "immediately" to mapOf(
            Language.ENGLISH to "immediately", Language.HINDI to "तुरंत", Language.GUJARATI to "તરત જ",
            Language.MARATHI to "त्वरित", Language.TAMIL to "உடனடியாக", Language.TELUGU to "వెంటనే",
            Language.KANNADA to "ತಕ್ಷಣ", Language.MALAYALAM to "ഉടൻ", Language.ODIA to "ତୁରନ୍ତ", Language.BENGALI to "অবিলম্বে"
        )
    )

    fun detectLanguage(text: String): Language? {
        for (ch in text) {
            when (ch) {
                in '\u0900'..'\u097F' -> return Language.HINDI
                in '\u0980'..'\u09FF' -> return Language.BENGALI
                in '\u0A80'..'\u0AFF' -> return Language.GUJARATI
                in '\u0B00'..'\u0B7F' -> return Language.ODIA
                in '\u0B80'..'\u0BFF' -> return Language.TAMIL
                in '\u0C00'..'\u0C7F' -> return Language.TELUGU
                in '\u0C80'..'\u0CFF' -> return Language.KANNADA
                in '\u0D00'..'\u0D7F' -> return Language.MALAYALAM
            }
        }
        if (text.any { it in 'a'..'z' || it in 'A'..'Z' }) {
            return Language.ENGLISH
        }
        return null
    }

    fun translate(text: String, sourceLang: Language, targetLang: Language): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return ""

        val detected = detectLanguage(trimmed)
        val effectiveSource = detected ?: sourceLang

        // Same-language optimization
        if (effectiveSource == targetLang && detected == targetLang) {
            return trimmed
        }

        val cleanInput = trimmed.replace("[।.,?!]".toRegex(), "").trim()

        // 1. Universal phrase lookup: Search across ALL languages in PHRASE_INDEX
        for ((_, translations) in PHRASE_INDEX) {
            // Check if cleanInput matches any language in this phrase
            for ((lang, phrase) in translations) {
                val cleanPhrase = phrase.replace("[।.,?!]".toRegex(), "").trim()
                if (cleanPhrase.equals(cleanInput, ignoreCase = true)) {
                    val targetPhrase = translations[targetLang]
                    if (targetPhrase != null) {
                        return targetPhrase
                    }
                }
            }
        }

        // 2. Substring phrase match across ALL languages
        for ((_, translations) in PHRASE_INDEX) {
            for ((lang, phrase) in translations) {
                val cleanPhrase = phrase.replace("[।.,?!]".toRegex(), "").trim()
                if (cleanPhrase.length >= 4 && cleanInput.contains(cleanPhrase, ignoreCase = true)) {
                    val targetPhrase = translations[targetLang]
                    if (targetPhrase != null) {
                        return targetPhrase
                    }
                }
            }
        }

        // 3. Word-level multilingual translation fallback
        val words = cleanInput.split("\\s+".toRegex())
        val translatedWords = mutableListOf<String>()
        var matchedAnyWord = false

        for (w in words) {
            val lowerW = w.lowercase()
            var translatedW = w
            for ((_, langMap) in VOCAB_INDEX) {
                // Check if w matches in any language
                val matchedEntry = langMap.entries.firstOrNull { it.value.equals(lowerW, ignoreCase = true) }
                if (matchedEntry != null) {
                    val tgtW = langMap[targetLang]
                    if (tgtW != null) {
                        translatedW = tgtW
                        matchedAnyWord = true
                        break
                    }
                }
            }
            translatedWords.add(translatedW)
        }

        if (matchedAnyWord) {
            val danda = if (targetLang in listOf(Language.HINDI, Language.BENGALI, Language.ODIA)) "।" else "."
            return translatedWords.joinToString(" ") + danda
        }

        // 4. Fallback: Return original text
        return trimmed
    }
}
